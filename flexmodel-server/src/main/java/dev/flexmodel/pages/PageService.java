package dev.flexmodel.pages;

import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.codegen.enumeration.PageDeploymentStatus;
import dev.flexmodel.common.FlexmodelConfig;
import dev.flexmodel.pages.dto.PageSiteResponse;
import dev.flexmodel.pages.dto.PageSiteUpdateRequest;
import dev.flexmodel.common.SessionContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pages 站点配置读写、部署编排、initPageSite()（项目创建时调用）、默认欢迎页生成。
 *
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class PageService {

  @Inject
  PageSiteRepository pageSiteRepository;

  @Inject
  PageDeployer pageDeployer;

  @Inject
  PageAliasManager pageAliasManager;

  @Inject
  FlexmodelConfig flexmodelConfig;

  @Inject
  FlexmodelConfig config;

  @Inject
  SessionContext sessionContext;

  /**
   * 获取项目的 Pages 站点配置
   */
  public PageSiteResponse getPageSite(String projectId) {
    PageSite site = pageSiteRepository.findByProjectId(projectId);
    if (site == null) {
      return null;
    }
    return PageSiteResponse.from(site);
  }

  /**
   * 更新 Pages 站点配置（custom_domains 等）
   */
  public PageSiteResponse updatePageSite(String projectId, PageSiteUpdateRequest request) {
    PageSite site = pageSiteRepository.findByProjectId(projectId);

    if (site == null) {
      site = new PageSite();
      site.setStatus(PageDeploymentStatus.READY);
      site.setFileCount(0);
      site.setSizeBytes(0L);
    }

    if (request.getCustomDomains() != null) {
      site.setCustomDomains(request.getCustomDomains());
    }

    site.setUpdatedBy(sessionContext.getUserId());
    site.setUpdatedAt(LocalDateTime.now());

    pageSiteRepository.save(projectId, site);
    log.info("Page site updated for project {}", projectId);

    return PageSiteResponse.from(site);
  }

  /**
   * 项目创建时调用：创建 f_page_site 记录 + 生成默认欢迎页 + 切 production 软链
   */
  public void initPageSite(String projectId) {
    log.info("Initializing page site for project {}", projectId);

    PageSite site = new PageSite();
    site.setStatus(PageDeploymentStatus.READY);
    site.setCreatedBy(sessionContext.getUserId() != null ? sessionContext.getUserId() : "system");
    site.setCreatedAt(LocalDateTime.now());

    String defaultDepId = "dep_" + UUID.randomUUID().toString().substring(0, 8);
    String defaultHtml = generateDefaultWelcomePage(projectId);

    PageDeployer.DeployResult result = pageDeployer.writeSingleFile(projectId, defaultDepId, "index.html", defaultHtml);

    pageAliasManager.createAlias(projectId, "production", defaultDepId);

    site.setProductionDeploymentId(defaultDepId);
    site.setFileCount(result.fileCount());
    site.setSizeBytes(result.sizeBytes());

    pageSiteRepository.save(projectId, site);
    log.info("Page site initialized with default welcome page for project {}", projectId);
  }

  /**
   * 上传 zip 部署。如果站点不存在则自动创建。
   */
  public PageSiteResponse deploy(String projectId, InputStream zipStream) {
    PageSite site = pageSiteRepository.findByProjectId(projectId);
    if (site == null) {
      // 已有项目可能没有 page_site 记录（项目创建时未初始化），自动创建
      log.info("Page site not found for project {}, auto-creating", projectId);
      site = new PageSite();
      site.setStatus(PageDeploymentStatus.READY);
      site.setFileCount(0);
      site.setSizeBytes(0L);
      site.setCreatedBy(sessionContext.getUserId() != null ? sessionContext.getUserId() : "system");
      site.setCreatedAt(LocalDateTime.now());
    }

    try {
      PageDeployer.DeployResult result = pageDeployer.deploy(projectId, zipStream);

      pageAliasManager.createAlias(projectId, "production", result.deploymentId());

      site.setProductionDeploymentId(result.deploymentId());
      site.setStatus(PageDeploymentStatus.READY);
      site.setFileCount(result.fileCount());
      site.setSizeBytes(result.sizeBytes());
      site.setErrorMessage(null);
      site.setUpdatedBy(sessionContext.getUserId());
      site.setUpdatedAt(LocalDateTime.now());

      pageSiteRepository.save(projectId, site);
      log.info("Deployed {} files ({}) to production for project {}", result.fileCount(), result.sizeBytes(), projectId);

      return PageSiteResponse.from(site);
    } catch (PageException e) {
      site.setStatus(PageDeploymentStatus.FAILED);
      site.setErrorMessage(e.getMessage());
      site.setUpdatedBy(sessionContext.getUserId());
      site.setUpdatedAt(LocalDateTime.now());
      pageSiteRepository.save(projectId, site);
      throw e;
    }
  }

  /**
   * 切生产别名（回滚到指定 deploymentId）
   */
  public PageSiteResponse setProduction(String projectId, String deploymentId) {
    PageSite site = pageSiteRepository.findByProjectId(projectId);
    if (site == null) {
      throw new PageException("Page site not found for project: " + projectId);
    }

    Path root = Paths.get(flexmodelConfig.pages().rootPath()).normalize();
    Path deploymentDir = root.resolve(projectId).resolve(deploymentId);
    if (!Files.exists(deploymentDir)) {
      throw new PageException("Deployment not found: " + deploymentId);
    }

    pageAliasManager.createAlias(projectId, "production", deploymentId);

    // 重新统计文件信息
    int fileCount = 0;
    long sizeBytes = 0;
    try {
      fileCount = (int) Files.walk(deploymentDir)
        .filter(Files::isRegularFile)
        .count();
      sizeBytes = Files.walk(deploymentDir)
        .filter(Files::isRegularFile)
        .mapToLong(p -> {
          try {
            return Files.size(p);
          } catch (IOException e) {
            return 0;
          }
        })
        .sum();
    } catch (IOException e) {
      log.warn("Failed to count files: {}", e.getMessage());
    }

    site.setProductionDeploymentId(deploymentId);
    site.setFileCount(fileCount);
    site.setSizeBytes(sizeBytes);
    site.setStatus(PageDeploymentStatus.READY);
    site.setErrorMessage(null);
    site.setUpdatedBy(sessionContext.getUserId());
    site.setUpdatedAt(LocalDateTime.now());

    pageSiteRepository.save(projectId, site);
    log.info("Production switched to {} for project {}", deploymentId, projectId);

    return PageSiteResponse.from(site);
  }

  private String generateDefaultWelcomePage(String projectId) {
    String siteUrl = config.pagesUrlTemplate()
      .replace("{{projectId}}", projectId);
    return """
      <!DOCTYPE html>
      <html lang="en">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Welcome - %s</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                 display: flex; justify-content: center; align-items: center; min-height: 100vh;
                 margin: 0; background: #f5f5f5; color: #333; }
          .container { text-align: center; padding: 2rem; }
          h1 { font-size: 2rem; margin-bottom: 1rem; }
          p { font-size: 1.1rem; color: #666; }
          .url { font-size: 0.9rem; color: #999; margin-top: 1.5rem; }
        </style>
      </head>
      <body>
        <div class="container">
          <h1>🚀 Your site is ready!</h1>
          <p>Upload your static files to deploy your application.</p>
          <div class="url">Site URL: %s</div>
        </div>
      </body>
      </html>
      """.formatted(projectId, siteUrl);
  }
}
