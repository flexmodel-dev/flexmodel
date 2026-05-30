package dev.flexmodel.branch;

import dev.flexmodel.codegen.entity.Branch;

import java.util.List;

/**
 * 分支仓库接口
 *
 * @author cjbi
 */
public interface BranchRepository {

  List<Branch> findByProjectId(String projectId);

  Branch findByProjectIdAndName(String projectId, String branchName);

  Branch save(Branch branch);

  void delete(String projectId, String branchName);

  List<Branch> findAll();
}
