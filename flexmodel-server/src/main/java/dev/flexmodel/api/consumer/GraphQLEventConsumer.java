package dev.flexmodel.api.consumer;

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

  public void handle(@Observes StartupEvent startupEvent) {
    consume(new GraphQLRefreshEvent());
  }

  @ConsumeEvent("graphql.refresh")
  public void consume(GraphQLRefreshEvent event) {
    long beginTime = System.currentTimeMillis();
    log.info("Received graphql message");
//    List<Project> projects = projectService.findProjects(event);
//    for (Project project : projects) {
//      Map<String, List<String>> dsMap = datasourceList.stream()
//        .filter(f -> f.getProjectId() != null)
//        .collect(groupingBy(Datasource::getProjectId, mapping(Datasource::getName, toList())));
//      FlexmodelGraphQL fg = new FlexmodelGraphQL();
//      graphQLManger.addDefaultGraphQL(fg.generateGraphQLWithSchemaObject(sf, sf.getSchemaNames()));
//      dsMap.forEach((projectId, datasourceNames) -> graphQLManger.addGraphQL(projectId, fg.generateGraphQLWithSchemaObject(sf, datasourceNames)));
//    }
    log.info("========== GraphQL init successful in {} ms!", System.currentTimeMillis() - beginTime);
  }

}
