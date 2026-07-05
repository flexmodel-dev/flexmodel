package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.session.Session;
import dev.flexmodel.session.SessionFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

import static dev.flexmodel.codegen.System.branch;

/**
 * 分支仓库实现
 *
 * @author cjbi
 */
@ApplicationScoped
public class BranchFmRepository implements BranchRepository {

  @Inject
  SessionFactory sessionFactory;

  @Override
  public List<Branch> findByProjectId(String projectId) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Branch.class)
        .where(branch.projectId.eq(projectId))
          .execute().stream()
          .toList();
    }
  }

  @Override
  public Branch findByProjectIdAndName(String projectId, String branchName) {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Branch.class)
        .where(branch.projectId.eq(projectId)
          .and(branch.name.eq(branchName)))
          .executeOne();
    }
  }

  @Override
  public Branch save(Branch branch) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().mergeInto(Branch.class)
          .values(branch).execute();
    }
    return branch;
  }

  @Override
  public void delete(String projectId, String branchName) {
    try (Session session = sessionFactory.createSession()) {
      session.dsl().deleteFrom(Branch.class)
        .where(branch.projectId.eq(projectId)
          .and(branch.name.eq(branchName)))
          .execute();
    }
  }

  @Override
  public List<Branch> findAll() {
    try (Session session = sessionFactory.createSession()) {
      return session.dsl().selectFrom(Branch.class)
          .execute().stream()
          .toList();
    }
  }
}
