package dev.flexmodel.pages.dto;

import dev.flexmodel.codegen.entity.PageSite;
import dev.flexmodel.codegen.enumeration.PageDeploymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@Data
@Builder
public class PageSiteResponse {

  private String id;
  private String projectId;
  private Object customDomains;
  private String productionDeploymentId;
  private String status;
  private int fileCount;
  private long sizeBytes;
  private String errorMessage;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static PageSiteResponse from(PageSite site) {
    return PageSiteResponse.builder()
      .id(site.getId())
      .customDomains(site.getCustomDomains())
      .productionDeploymentId(site.getProductionDeploymentId())
      .status(site.getStatus() != null ? site.getStatus().name() : "READY")
      .fileCount(site.getFileCount() != null ? site.getFileCount() : 0)
      .sizeBytes(site.getSizeBytes() != null ? site.getSizeBytes() : 0L)
      .errorMessage(site.getErrorMessage())
      .createdBy(site.getCreatedBy())
      .updatedBy(site.getUpdatedBy())
      .createdAt(site.getCreatedAt())
      .updatedAt(site.getUpdatedAt())
      .build();
  }
}
