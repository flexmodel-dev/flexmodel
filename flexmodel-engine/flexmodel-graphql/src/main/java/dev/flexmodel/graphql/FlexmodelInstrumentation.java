package dev.flexmodel.graphql;

import graphql.ExecutionResult;
import graphql.execution.ExecutionContext;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.instrumentation.DocumentAndVariables;
import graphql.execution.instrumentation.Instrumentation;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldParameters;
import graphql.language.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Flexmodel GraphQL instrumentation, handling @transform and @export directives.
 *
 * @author cjbi
 */
public class FlexmodelInstrumentation implements Instrumentation {

  private static final Logger log = LoggerFactory.getLogger(FlexmodelInstrumentation.class);

  @Override
  public InstrumentationState createState(InstrumentationCreateStateParameters parameters) {
    return new FlexmodelInstrumentationState();
  }

  @Override
  public DocumentAndVariables instrumentDocumentAndVariables(DocumentAndVariables documentAndVariables,
                                                             InstrumentationExecutionParameters parameters,
                                                             InstrumentationState state) {
    final FlexmodelInstrumentationState flexState = (FlexmodelInstrumentationState) state;
    Document document = documentAndVariables.getDocument();

    // Build fragment definition map for resolving fragment spreads
    Map<String, FragmentDefinition> fragmentMap = document.getDefinitionsOfType(FragmentDefinition.class)
      .stream()
      .collect(Collectors.toMap(FragmentDefinition::getName, fd -> fd));

    // Recursively walk the AST to collect @transform and @export directives
    for (Definition<?> definition : document.getDefinitions()) {
      if (definition instanceof OperationDefinition opDef) {
        collectDirectives(opDef.getSelectionSet(), "", fragmentMap, flexState);
      }
    }

    return documentAndVariables;
  }

  /**
   * Recursively walk the AST selection set to collect @transform and @export directives.
   */
  private void collectDirectives(SelectionSet selectionSet, String parentPath,
                                 Map<String, FragmentDefinition> fragmentMap,
                                 FlexmodelInstrumentationState flexState) {
    if (selectionSet == null) return;
    for (Selection<?> selection : selectionSet.getSelections()) {
      if (selection instanceof Field field) {
        String fieldName = field.getAlias() != null ? field.getAlias() : field.getName();
        String fieldPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;

        for (Directive directive : field.getDirectives()) {
          if ("transform".equals(directive.getName())) {
            String getValue = extractStringValue(directive.getArgument("get"));
            if (getValue != null) {
              flexState.addTransform(fieldPath, getValue);
            }
          } else if ("export".equals(directive.getName())) {
            String asValue = extractStringValue(directive.getArgument("as"));
            if (asValue != null) {
              flexState.addExportDirective(fieldPath, asValue);
            }
          }
        }

        collectDirectives(field.getSelectionSet(), fieldPath, fragmentMap, flexState);
      } else if (selection instanceof InlineFragment inlineFragment) {
        collectDirectives(inlineFragment.getSelectionSet(), parentPath, fragmentMap, flexState);
      } else if (selection instanceof FragmentSpread fragmentSpread) {
        FragmentDefinition fragmentDef = fragmentMap.get(fragmentSpread.getName());
        if (fragmentDef != null) {
          collectDirectives(fragmentDef.getSelectionSet(), parentPath, fragmentMap, flexState);
        }
      }
    }
  }

  /**
   * Safely extract a string value from a directive argument.
   * Handles both StringValue and VariableReference to avoid ClassCastException.
   */
  private String extractStringValue(Argument argument) {
    if (argument == null) return null;
    Value<?> value = argument.getValue();
    if (value instanceof StringValue sv) {
      return sv.getValue();
    }
    if (value instanceof VariableReference vr) {
      log.warn("Directive argument uses variable reference '{}' which is not fully supported, using variable name as fallback", vr.getName());
      return vr.getName();
    }
    return null;
  }

  @Override
  public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult,
                                                                      InstrumentationExecutionParameters parameters,
                                                                      InstrumentationState state) {
    Object data = executionResult.getData();
    Map<String, String> transformMap = ((FlexmodelInstrumentationState) state).getTransformMap();

    if (data != null && !transformMap.isEmpty()) {
      transformMap.forEach((path, getExpr) -> {
        try {
          Object originValue = evaluate(path, data);
          if (originValue != null) {
            Object newValue = evaluate(getExpr, originValue);
            setNestedValue(data, path, newValue);
          }
        } catch (Exception e) {
          log.error("Execution result transform error: {}, path={}, get={}", e.getMessage(), path, getExpr);
        }
      });
    }

    return CompletableFuture.completedFuture(executionResult);
  }

  /**
   * Navigate through nested data using a dot-separated path and return the value at the target.
   */
  private Object evaluate(String path, Object data) {
    String[] keys = path.split("\\.");
    Object result = data;
    for (String key : keys) {
      result = navigate(result, key);
      if (result == null) return null;
    }
    return result;
  }

  /**
   * Navigate one level into the data structure (Map, List, or Array).
   */
  @SuppressWarnings("rawtypes")
  private Object navigate(Object data, String key) {
    if (data instanceof Map map) {
      return map.get(key);
    }
    if (data instanceof List<?> list) {
      try {
        int index = Integer.parseInt(key);
        return (index >= 0 && index < list.size()) ? list.get(index) : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }
    if (data != null && data.getClass().isArray()) {
      try {
        int index = Integer.parseInt(key);
        int length = Array.getLength(data);
        return (index >= 0 && index < length) ? Array.get(data, index) : null;
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  /**
   * Set a value at a nested path in the data structure.
   * Supports both dot-separated paths for Maps and List elements.
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void setNestedValue(Object data, String path, Object newValue) {
    String[] parts = path.split("\\.");
    if (parts.length == 0) return;

    if (parts.length == 1) {
      if (data instanceof Map map) {
        map.put(parts[0], newValue);
      }
      return;
    }

    // Navigate to the parent of the target field
    Object current = data;
    for (int i = 0; i < parts.length - 1; i++) {
      current = navigate(current, parts[i]);
      if (current == null) return;
    }

    String lastKey = parts[parts.length - 1];
    if (current instanceof Map map) {
      map.put(lastKey, newValue);
    } else if (current instanceof List<?> list) {
      // If parent is a list, apply transform to each element
      for (Object elem : list) {
        if (elem instanceof Map elemMap) {
          Object elemOriginValue = elemMap.get(lastKey);
          if (elemOriginValue != null) {
            Object elemNewValue = evaluate(newValue.toString(), elemOriginValue);
            elemMap.put(lastKey, elemNewValue);
          }
        }
      }
    }
  }

  @Override
  public InstrumentationContext<Object> beginFieldExecution(InstrumentationFieldParameters parameters,
                                                            InstrumentationState state) {
    ExecutionContext executionContext = parameters.getExecutionContext();
    ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();
    final FlexmodelInstrumentationState flexState = (FlexmodelInstrumentationState) state;
    return new InstrumentationContext<>() {
      @Override
      public void onDispatched() {}

      @Override
      public void onCompleted(Object result, Throwable t) {
        Map<String, String> exportDirectiveMap = flexState.getExportDirectiveMap();

        String s = executionStepInfo.getPath()
          .toString()
          .substring(1)
          .replace("/", ".")
          .replaceAll("\\[\\d+\\]", "");

        exportDirectiveMap.forEach((key, value) -> {
          if (key.equals(s)) {
            executionContext.getGraphQLContext().compute("__VARIABLES", (k, v) -> {
              Map<String, Object> variables = (Map) v;
              if (variables == null) {
                variables = new HashMap<>();
              }
              variables.put(value, t == null ? result : null);
              return variables;
            });
          }
        });
      }
    };
  }

}
