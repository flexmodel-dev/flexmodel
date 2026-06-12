package dev.flexmodel.api.consumer;

import dev.flexmodel.project.BranchService;
import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.graphql.FlexmodelGraphQL;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.api.GraphQLManager;
import dev.flexmodel.api.dto.GraphQLRefreshEvent;
import dev.flexmodel.project.ProjectService;
import dev.flexmodel.session.SessionFactory;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.*;

/**
 * 监听GraphQL变更事件
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class GraphQLEventConsumer {

  @Inject
  SessionFactory sf;
  @Inject
  GraphQLManager graphQLManger;
  @Inject
  ProjectService projectService;
  @Inject
  BranchService branchService;

  public void handle(@Observes StartupEvent startupEvent) {
    consume(new GraphQLRefreshEvent());
  }

  @ConsumeEvent("graphql.refresh")
  public void consume(GraphQLRefreshEvent event) {
    long beginTime = System.currentTimeMillis();
    log.info("Received graphql message");
    // 仅刷新顶级项目（分支项目通过 refreshProject 内部处理）
    List<Project> projects = projectService.findTopLevelProjects();
    for (Project project : projects) {
      refreshProject(project);
    }
    log.info("========== GraphQL init successful in {} ms!", System.currentTimeMillis() - beginTime);
  }

  /**
   * 刷新单个项目的 GraphQL Schema。
   * 用于新项目创建后同步注册 GraphQL，避免全量刷新。
   *
   * @param project 项目
   */
  public void refreshProject(Project project) {
    try {
      List<Branch> branches = branchService.listBranches(project.getId());
      for (Branch branch : branches) {
        String databaseName = branch.getDatabaseName();
        // main 分支使用父项目 ID，其他分支使用复合 ID
        String graphqlKey = "main".equals(branch.getName())
          ? project.getId()
          : project.getId() + "_" + branch.getName();
        log.info("Refreshing GraphQL for project '{}', schemaName='{}', graphqlKey='{}'",
          project.getId(), databaseName, graphqlKey);

        FlexmodelGraphQL fg = new FlexmodelGraphQL();
        graphQLManger.addGraphQL(
          graphqlKey,
          fg.generateGraphQLWithSchemaObject(sf, databaseName)
        );
        log.info("GraphQL schema generated for '{}', models={}", graphqlKey, sf.getModels(databaseName).size());
      }
    } catch (Exception e) {
      log.warn("Failed to generate GraphQL for project '{}': {}", project.getId(), e.getMessage(), e);
    }
  }

  /**
   * 移除指定项目的 GraphQL Schema。
   */
  public void removeProject(String projectId) {
    graphQLManger.removeGraphQL(projectId);
  }

}
