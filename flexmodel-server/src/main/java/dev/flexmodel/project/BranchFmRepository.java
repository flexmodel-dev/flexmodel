package dev.flexmodel.project;

import dev.flexmodel.codegen.entity.Branch;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.session.Session;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * 分支仓库实现
 *
 * @author cjbi
 */
@ApplicationScoped
public class BranchFmRepository implements BranchRepository {

  @Inject
  Session session;

  @Override
  public List<Branch> findByProjectId(String projectId) {
    return session.dsl().selectFrom(Branch.class)
        .where(Expressions.field(Branch::getProjectId).eq(projectId))
        .execute().stream()
        .toList();
  }

  @Override
  public Branch findByProjectIdAndName(String projectId, String branchName) {
    return session.dsl().selectFrom(Branch.class)
        .where(Expressions.field(Branch::getProjectId).eq(projectId)
            .and(Expressions.field(Branch::getName).eq(branchName)))
        .executeOne();
  }

  @Override
  public Branch save(Branch branch) {
    session.dsl().mergeInto(Branch.class)
        .values(branch).execute();
    return branch;
  }

  @Override
  public void delete(String projectId, String branchName) {
    session.dsl().deleteFrom(Branch.class)
        .where(Expressions.field(Branch::getProjectId).eq(projectId)
            .and(Expressions.field(Branch::getName).eq(branchName)))
        .execute();
  }

  @Override
  public List<Branch> findAll() {
    return session.dsl().selectFrom(Branch.class)
        .execute().stream()
        .toList();
  }
}
