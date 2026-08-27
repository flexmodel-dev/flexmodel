package dev.flexmodel.flow.dto;

import lombok.Getter;
import lombok.Setter;
import dev.flexmodel.codegen.entity.FlowDefinition;

import java.time.LocalDateTime;

/**
 * @author cjbi
 */
@Getter
@Setter
public class FlowModuleResponse {

  private String flowModuleId;
  private String flowName;
  private String flowKey;
  private Integer status;
  private String remark;
  private String createdBy;
  private String updatedBy;
  private LocalDateTime modifyTime;

  public FlowModuleResponse() {
  }

  public FlowModuleResponse(FlowDefinition flowDefinition) {
    this.flowModuleId = flowDefinition.getFlowModuleId();
    this.flowName = flowDefinition.getFlowName();
    this.flowKey = flowDefinition.getFlowKey();
    this.status = flowDefinition.getStatus();
    this.remark = flowDefinition.getRemark();
    this.modifyTime = flowDefinition.getUpdatedAt();
    this.updatedBy = flowDefinition.getUpdatedBy();
  }

}
