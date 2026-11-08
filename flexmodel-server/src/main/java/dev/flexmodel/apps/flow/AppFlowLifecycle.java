package dev.flexmodel.apps.flow;

import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.flexmodel.apps.flow.AppFlowJson.config;
import static dev.flexmodel.apps.flow.AppFlowJson.flatten;
import static dev.flexmodel.common.Values.*;

@ApplicationScoped
public class AppFlowLifecycle {
  @Inject
  AppFlowInstanceRepository appFlowInstanceRepository;
  @Inject
  AppFlowDeploymentRepository appFlowDeploymentRepository;
  @Inject
  AppFlowService appFlowService;
  @Inject
  AppFlowBindings bindings;

  private boolean active(Map<String, Object> run) {
    return !Set.of("SUCCEEDED", "REJECTED", "TERMINATED").contains(text(run, "status"));
  }

  private List<Map<String, Object>> protectedDefinitions(String appId) {
    List<Map<String, Object>> definitions = new ArrayList<>();
    for (var binding : bindings.active(appId))
      definitions.add(appFlowDeploymentRepository.deployedFlow(appId, binding.getDeploymentId()));
    for (var run : appFlowInstanceRepository.findAll(appId))
      if (active(run))
        definitions.add(appFlowDeploymentRepository.deployedFlow(appId, text(run, "flow_deploy_id")));
    return definitions;
  }

  public void beforeDeleteForm(String appId, String form) {
    for (var flow : protectedDefinitions(appId))
      if (text(object(flow.get("form")), "name").equals(form) || flatten(objects(object(flow.get("definition")).get("nodes"))).stream().anyMatch(node -> text(object(config(node).get("form")), "name").equals(form)))
        throw new ValidationException("表单正在被已启用或运行中的流程使用，请先停用流程并结束相关审批");
  }

  public void beforeDeleteField(String appId, String form, String field) {
    for (var flow : protectedDefinitions(appId)) {
      var trigger = FlowTrigger.of(flow);
      String model = dev.flexmodel.JsonUtils.toJsonString(flow.get("definition"));
      if (form.equals(text(trigger, "formName")) &&
        (strings(trigger.get("watchedFields")).contains(field) || model.contains("record." + field)))
        throw new ValidationException("字段正在被已启用或运行中的流程引用");
      for (var node : flatten(objects(object(flow.get("definition")).get("nodes")))) {
        var cfg = config(node);
        if (!form.equals(text(object(cfg.get("form")), "name"))) continue;
        boolean direct = java.util.stream.Stream.concat(objects(cfg.get("conditions")).stream(), objects(cfg.get("assignments")).stream())
          .anyMatch(entry -> field.equals(text(entry, "field")));
        String output = text(cfg, "output");
        boolean referenced = model.contains("nodes." + text(node, "id") + "." + field)
          || model.contains("nodes." + text(node, "id") + ".0." + field)
          || !output.isBlank() && (model.contains(output + "." + field) || model.contains(output + ".0." + field));
        if (direct || referenced) throw new ValidationException("字段正在被已启用或运行中的流程引用");
      }
    }
  }
}
