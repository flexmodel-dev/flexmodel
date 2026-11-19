package dev.flexmodel.common.config;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.codegen.entity.Project;
import dev.flexmodel.common.SchemaRegistry;
import dev.flexmodel.project.BranchRepository;
import dev.flexmodel.project.ProjectService;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineConfigTest {
  @Test
  void registersMainSchemasEvenWithoutBranchRowsAndDeduplicatesBranches() {
    Project main = new Project();
    main.setId("plm_system");
    main.setDatabaseName("plm_system");
    ProjectService projects = new ProjectService() {
      @Override
      public List<Project> findProjects() {
        return List.of(main);
      }
    };
    List<String> registered = new ArrayList<>();
    SchemaRegistry registry = new SchemaRegistry() {
      @Override
      public void registerSchema(String schema) {
        registered.add(schema);
      }
    };
    BranchRepository empty = branches(List.of());
    new EngineConfig().installDatasource(null, projects, registry, empty);
    assertEquals(List.of("plm_system"), registered);

    Branch duplicateMain = new Branch();
    duplicateMain.setDatabaseName("plm_system");
    Branch branch = new Branch();
    branch.setDatabaseName("plm_system_preview");
    registered.clear();
    new EngineConfig().installDatasource(null, projects, registry, branches(List.of(duplicateMain, branch)));
    assertEquals(List.of("plm_system", "plm_system_preview"), registered);
  }

  private BranchRepository branches(List<Branch> branches) {
    return (BranchRepository) Proxy.newProxyInstance(BranchRepository.class.getClassLoader(),
      new Class<?>[]{BranchRepository.class}, (proxy, method, args) -> {
        if (method.getName().equals("findByProjectId")) return branches;
        throw new UnsupportedOperationException(method.getName());
      });
  }
}
