package dev.flexmodel.common.config.web.vertx;

import dev.flexmodel.project.ProjectService;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import dev.flexmodel.api.ApiRuntimeService;
import dev.flexmodel.api.dto.GraphQLRefreshEvent;
import dev.flexmodel.project.dto.ProjectListRequest;
import dev.flexmodel.project.dto.ProjectResponse;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.FlexmodelConfig;

import java.util.List;


/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class FlexmodelRestAPIHandler {

  @Inject
  ApiRuntimeService apiRuntimeService;
  @Inject
  EventBus eventBus;
  @Inject
  FlexmodelConfig config;
  @Inject
  ProjectService projectService;

  void handle(@Observes StartupEvent startupEvent, Router router) {
    List<ProjectResponse> projects = projectService.findProjects(new ProjectListRequest(null));
    for (Project project : projects) {
      router.route()
        .pathRegex(config.apiRootPath() + "/" + project.getId() + "/.*")
        .handler(BodyHandler.create())
        .blockingHandler(apiRuntimeService::accept);
    }

    router.route().pathRegex("/v1/datasources.*")
      .handler(handle -> {

        handle.addEndHandler(v -> {
          if (handle.request().method() != HttpMethod.GET) {
            log.info(">>> Refreshing GraphQL schema...");
            eventBus.send("graphql.refresh", new GraphQLRefreshEvent());
          }
        });
        handle.next();
      });
  }

}
