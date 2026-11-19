package dev.flexmodel.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentRequest {

  private String id;
  private String name;
  private String parentId;
  private String createdBy;
  private String updatedBy;
}
