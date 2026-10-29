package dev.flexmodel.apps;

import dev.flexmodel.apps.dto.AppFormFieldResponse;
import dev.flexmodel.apps.dto.AppFormResponse;
import dev.flexmodel.common.ValidationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

/** A subform link can be an ordinary string field; expose its logical reference in transfers. */
@ApplicationScoped
public class AppTransferForms {
  @Inject AppFormService forms;

  Map<String, AppFormResponse> all(String appId) {
    var source = forms.findForms(appId);
    Map<String, Map<String, String>> links = new HashMap<>();
    for (var parent : source) {
      if (parent.builtin() || parent.layout() == null || parent.layout().items() == null) continue;
      for (var item : parent.layout().items()) if ("SUBFORM".equals(item.type())) {
        var fields = links.computeIfAbsent(item.formName(), ignored -> new HashMap<>());
        String previous = fields.putIfAbsent(item.referenceField(), parent.name());
        if (previous != null && !previous.equals(parent.name()))
          throw new ValidationException("子表单关联列指向多个主表单，请先修正布局: " + item.formName() + "." + item.referenceField());
      }
    }
    Map<String, AppFormResponse> result = new LinkedHashMap<>();
    for (var form : source) {
      var fields = form.fields().stream().map(field -> {
        String parent = links.getOrDefault(form.name(), Map.of()).get(field.name());
        if (parent == null || field.builtin()) return field;
        if (field.referenceForm() != null && !parent.equals(field.referenceForm()))
          throw new ValidationException("子表单关联列与字段引用目标不一致: " + form.label() + "." + field.label());
        return new AppFormFieldResponse(field.name(), field.label(), field.type(), field.required(), false,
          field.options(), parent, field.locked(), field.extension(), field.config());
      }).toList();
      result.put(form.name(), new AppFormResponse(form.name(), form.label(), form.description(), form.displayField(), form.searchField(),
        form.managed(), form.builtin(), form.createdAt(), form.updatedAt(), fields, form.layout(), form.viewTabs()));
    }
    return result;
  }
}
