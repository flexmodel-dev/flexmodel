package dev.flexmodel.flow.service;

import dev.flexmodel.common.messaging.ReliableMessageHandler;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.flow.common.ErrorEnum;
import dev.flexmodel.flow.dto.param.*;
import dev.flexmodel.flow.dto.result.CommonResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * External handlers publish commands; no domain-specific dependency belongs here.
 */
@ApplicationScoped
public class FlowCommandHandler implements ReliableMessageHandler {
  @Inject
  ProcessService processService;
  @Inject
  SessionContext sessionContext;

  public String type() {
    return "flow.command";
  }

  @SuppressWarnings("unchecked")
  public void handle(String projectId, String messageId, Map<String, Object> command) {
    String previousPrincipal = sessionContext.getUserId();
    boolean previousAuthenticatedUser = sessionContext.isAuthenticatedUser();
    sessionContext.setUserId(java.util.Objects.toString(command.get("principal"), ""));
    sessionContext.setAuthenticatedUser(false);
    try {
    ExternalTaskAccess.as(String.valueOf(command.get("handler")), () -> {
      String action = String.valueOf(command.get("action"));
      CommonResult result;
      if (action.equals("start")) {
        StartProcessParam param = new StartProcessParam();
        param.setProjectId(projectId);
        param.setFlowDeployId(String.valueOf(command.get("deploymentId")));
        param.setInitiator(java.util.Objects.toString(command.get("initiator"), ""));
        param.setVariables((Map<String, Object>) command.getOrDefault("variables", Map.of()));
        result = processService.startProcess(param);
      } else if (action.equals("complete")) {
        CommitTaskParam param = new CommitTaskParam();
        param.setProjectId(projectId);
        param.setFlowInstanceId(String.valueOf(command.get("instanceId")));
        param.setTaskInstanceId(String.valueOf(command.get("taskId")));
        param.setVariables((Map<String, Object>) command.getOrDefault("variables", Map.of()));
        result = processService.commitTask(param);
      } else if (action.equals("terminate")) {
        result = processService.terminateProcess(projectId, String.valueOf(command.get("instanceId")));
      } else throw new IllegalArgumentException("未知流程命令: " + action);
      if (result == null || !ErrorEnum.isSuccess(result.getErrCode()))
        throw new IllegalStateException(result == null ? "流程执行未返回结果" : result.getErrMsg());
      return null;
    });
    } finally {
      sessionContext.setUserId(previousPrincipal);
      sessionContext.setAuthenticatedUser(previousAuthenticatedUser);
    }
  }
}
