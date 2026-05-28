package dev.flexmodel.api.consumer;

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

  public void handle(@Observes StartupEvent startupEvent) {
    consume(new GraphQLRefreshEvent());
  }

  @ConsumeEvent("graphql.refresh")
  public void consume(GraphQLRefreshEvent event) {
    long beginTime = System.currentTimeMillis();
    log.info("Received graphql message");
    List<Project> projects = projectService.findProjects();
    for (Project project : projects) {
      FlexmodelGraphQL fg = new FlexmodelGraphQL();
//      graphQLManger.addDefaultGraphQL(fg.generateGraphQLWithSchemaObject(sf, sf.getSchemaNames()));
      graphQLManger.addGraphQL(project.getId(), fg.generateGraphQLWithSchemaObject(sf, List.of(project.getDatabaseName())));
    }
    log.info("========== GraphQL init successful in {} ms!", System.currentTimeMillis() - beginTime);
  }

}
