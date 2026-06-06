package dev.flexmodel.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合并分支请求 DTO
 *
 * @author cjbi
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchMergeRequest {

  /** 源分支名称（要合并的分支） */
  private String sourceBranch;

  /** 目标分支名称（合并到的分支，默认 main） */
  private String targetBranch;

  /** 冲突策略：OVERWRITE（覆盖） / SKIP（跳过） */
  private ConflictStrategy conflictStrategy;

  public enum ConflictStrategy {
    OVERWRITE,
    SKIP
  }
}
