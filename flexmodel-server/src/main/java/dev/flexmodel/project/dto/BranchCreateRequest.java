package dev.flexmodel.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建分支请求 DTO
 *
 * @author cjbi
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchCreateRequest {

  /** 分支名称 */
  private String name;

  /** 来源分支（默认 main） */
  private String sourceBranch;

  /** 分支描述 */
  private String description;
}
