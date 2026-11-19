package dev.flexmodel.apps.flow;

import dev.flexmodel.common.messaging.ReliableMessageHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class AppFlowScheduledAdmission implements ReliableMessageHandler {
  @Inject
  AppFlowBindings bindings;
  @Inject
  AppFlowRuntime runtime;

  public String type() {
    return "flow.schedule.fire";
  }

  public void handle(String app, String message, Map<String, Object> payload) {
    var binding = bindings.find(app, text(payload, "flowId"));
    if (binding == null || !Objects.equals(binding.getDeploymentId(), text(payload, "deploymentId"))
      || ((Number) payload.get("generation")).intValue() != binding.getGeneration()) {
      bindings.log(app, text(payload, "flowId"), message, "SKIPPED_STALE", "", "触发绑定已经变化");
      return;
    }
    runtime.automatic(app, binding, message, Map.of("type", "SCHEDULE", "scheduledAt", text(payload, "scheduledAt")), Map.of());
  }
}
