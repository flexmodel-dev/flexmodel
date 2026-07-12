package dev.flexmodel.scheduling;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.codegen.entity.JobExecutionLog;
import dev.flexmodel.codegen.entity.Trigger;
import dev.flexmodel.common.SessionContext;
import dev.flexmodel.event.ChangedEvent;
import dev.flexmodel.event.EventListener;
import dev.flexmodel.event.EventType;
import dev.flexmodel.event.PreChangeEvent;
import dev.flexmodel.flow.dto.StartProcessParamEvent;
import dev.flexmodel.functions.FunctionService;
import io.vertx.mutiny.core.eventbus.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static dev.flexmodel.codegen.System.trigger;

/**
 * @author cjbi
 */
@Slf4j
@ApplicationScoped
public class TriggerDataChangedEventListener implements EventListener {

  @Inject
  TriggerRepository triggerRepository;
  @Inject
  JobExecutionLogService jobExecutionLogService;
  @Inject
  FunctionService functionService;
  @Inject
  EventBus eventBus;

  @Inject
  SessionContext sessionContext;

  private final Map<String, String> beforeMutationTypeMap = Map.of(
    "delete", "PRE_DELETE",
    "update", "PRE_UPDATE",
    "create", "PRE_INSERT"
  );

  private final Map<String, String> afterMutationTypeMap = Map.of(
    "delete", "DELETED",
    "update", "UPDATED",
    "create", "INSERTED"
  );

  @Override
  public void onPreChange(PreChangeEvent event) {
    try {
      String groupName = event.getSchemaName() + "_" + event.getModelName();
      String projectId = sessionContext.getProjectId();
      // 最多支持触发100个事件
      List<Trigger> triggers = triggerRepository.find(projectId,
        trigger.jobGroup.eq(groupName)
          .and(trigger.state.eq(true)), 1, 100);

      for (Trigger trigger : triggers) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) trigger.getConfig();
        String triggerTiming = (String) config.get("triggerTiming");
        if (triggerTiming.equals("before")) {
          @SuppressWarnings("unchecked")
          List<String> mutationTypes = (List<String>) config.get("mutationTypes");
          for (String mutationType : mutationTypes) {
            String eventType = beforeMutationTypeMap.get(mutationType);
            if (eventType.equals(event.getEventType())) {
              log.info("触发前置定时任务: triggerId={}, eventType={}, schemaName={}, modelName={}",
                trigger.getId(), eventType, event.getSchemaName(), event.getModelName());

              // 记录事件触发日志
              String logId = recordEventTriggerLog(trigger, event, "PRE_CHANGE", mutationType);

              dispatchEventTrigger(trigger, event.getNewData(), logId);
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("处理前置数据变更事件异常", e);
    }
  }

  @Override
  public void onChanged(ChangedEvent event) {
    try {
      String groupName = event.getSchemaName() + "_" + event.getModelName();
      String projectId = sessionContext.getProjectId();
      // 最多支持触发100个事件
      List<Trigger> triggers = triggerRepository.find(projectId,
        trigger.jobGroup.eq(groupName)
          .and(trigger.state.eq(true)), 1, 100);

      for (Trigger trigger : triggers) {
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) trigger.getConfig();
        String triggerTiming = (String) config.get("triggerTiming");
        if (triggerTiming.equals("after")) {
          @SuppressWarnings("unchecked")
          List<String> mutationTypes = (List<String>) config.get("mutationTypes");
          for (String mutationType : mutationTypes) {
            String eventType = afterMutationTypeMap.get(mutationType);
            if (eventType.equals(event.getEventType())) {
              log.info("触发后置定时任务: triggerId={}, eventType={}, schemaName={}, modelName={}",
                trigger.getId(), eventType, event.getSchemaName(), event.getModelName());

              // 记录事件触发日志
              String logId = recordEventTriggerLog(trigger, event, "POST_CHANGE", mutationType);

              @SuppressWarnings("unchecked")
              Map<String, Object> variables = JsonUtils.convertValue(event.getNewData(), Map.class);
              dispatchEventTrigger(trigger, variables, logId);
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("处理后置数据变更事件异常", e);
    }
  }

  @Override
  public boolean supports(String eventType) {
    return !eventType.equals(EventType.PRE_QUERY.getValue());
  }

  /**
   * 根据触发器任务类型分派执行（流程或云函数）
   */
  private void dispatchEventTrigger(Trigger trigger, Object eventData, String logId) {
    String projectId = sessionContext.getProjectId();
    if ("FUNCTION".equals(trigger.getJobType())) {
      functionService.invoke(projectId, trigger.getJobId(), Map.of(
        "triggerId", trigger.getId(),
        "eventData", eventData,
        "triggerTime", System.currentTimeMillis()
      ));
    } else {
      StartProcessParamEvent startProcessParam = new StartProcessParamEvent();
      startProcessParam.setProjectId(projectId);
      startProcessParam.setUserId(sessionContext.getUserId());
      startProcessParam.setFlowModuleId(trigger.getJobId());
      @SuppressWarnings("unchecked")
      Map<String, Object> variables = JsonUtils.convertValue(eventData, Map.class);
      startProcessParam.setVariables(variables);
      startProcessParam.setEventId(logId);
      startProcessParam.setStartTime(System.currentTimeMillis());

      eventBus.send("flow.start", startProcessParam);
    }
  }

  /**
   * 记录事件触发日志
   */
  private String recordEventTriggerLog(Trigger trigger, Object event, String triggerPhase, String mutationType) {
    try {
      // 构建输入数据
      Map<String, Object> inputData = Map.of(
        "triggerId", trigger.getId(),
        "triggerName", trigger.getName(),
        "triggerPhase", triggerPhase,
        "mutationType", mutationType,
        "eventData", event,
        "triggerTime", System.currentTimeMillis()
      );

      // 记录事件触发日志
      JobExecutionLog jobExecutionLog = jobExecutionLogService.recordJobStart(
        trigger.getId(),
        trigger.getJobId(),
        trigger.getJobGroup(),
        trigger.getJobType(),
        trigger.getName(),
        "EventTrigger",
        "EventTriggerInstance",
        System.currentTimeMillis(),
        System.currentTimeMillis(),
        inputData,
        sessionContext.getProjectId()
      );

      log.debug("已记录事件触发日志: triggerId={}, phase={}, mutationType={}",
        trigger.getId(), triggerPhase, mutationType);
      return jobExecutionLog.getId();
    } catch (Exception e) {
      log.error("记录事件触发日志失败: triggerId={}, phase={}", trigger.getId(), triggerPhase, e);
    }
    return null;
  }
}
