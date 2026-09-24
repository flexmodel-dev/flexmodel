package dev.flexmodel.service;

import dev.flexmodel.model.field.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.flexmodel.JsonUtils;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.ModelDefinition;
import dev.flexmodel.query.Query;
import dev.flexmodel.session.AbstractSessionContext;
import dev.flexmodel.sql.SqlExecutionException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static dev.flexmodel.query.Expressions.field;

/**
 * 基础服务类，提供查询验证、关联字段处理、嵌套查询、字段值生成等核心功能
 *
 * @author cjbi
 */
public abstract class BaseService {

  protected final Logger log = LoggerFactory.getLogger(this.getClass());

  private final AbstractSessionContext sessionContext;

  public BaseService(AbstractSessionContext sessionContext) {
    this.sessionContext = sessionContext;
    log.debug("BaseService initialized with sessionContext: {}", sessionContext.getClass().getSimpleName());
  }

  /**
   * 获取数据操作实现类
   *
   * @return 数据服务实例，子类需要重写此方法
   */
  public DataService getDataService() {
    throw new UnsupportedOperationException("getDataService() must be overridden by subclass");
  }

  /**
   * 验证查询的合法性
   * 包括：分组字段验证、连接字段验证等
   *
   * @param modelName 模型名称
   * @param query 查询对象
   * @throws RuntimeException 当查询验证失败时抛出
   * @throws SqlExecutionException 当连接配置错误时抛出
   */
  public void validateQuery(String modelName, Query query) {
    log.debug("Starting query validation for model: {}, query: {}", modelName, query);

    try {
      validateGroupByFields(query);
      validateJoinFields(modelName, query);
      log.debug("Query validation completed successfully for model: {}", modelName);
    } catch (Exception e) {
      log.error("Query validation failed for model: {}, error: {}", modelName, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 验证分组字段的合法性
   * 确保所有非聚合字段都在GROUP BY子句中
   *
   * @param query 查询对象
   * @throws RuntimeException 当分组字段验证失败时抛出
   */
  private void validateGroupByFields(Query query) {
    Query.Projection projection = query.getProjection();
    Query.GroupBy groupBy = query.getGroupBy();

    if (groupBy == null) {
      log.debug("No GROUP BY clause found, skipping group validation");
      return;
    }

    log.debug("Validating GROUP BY fields, groupBy: {}", groupBy);

    Set<String> groupedFieldNames = groupBy.getFields().stream()
      .map(Query.QueryField::getName)
      .collect(Collectors.toSet());

    log.debug("Grouped field names: {}", groupedFieldNames);

    List<String> ungroupedFieldNames = new ArrayList<>();

    if (projection != null) {
      Map<String, Query.QueryCall> projectionFields = projection.getFields();
      log.debug("Validating {} projection fields", projectionFields.size());

      for (Map.Entry<String, Query.QueryCall> entry : projectionFields.entrySet()) {
        String fieldAlias = entry.getKey();
        Query.QueryCall fieldExpression = entry.getValue();

        if (fieldExpression instanceof Query.QueryField queryField) {
          if (!groupedFieldNames.contains(fieldAlias) && !groupedFieldNames.contains(queryField.getName())) {
            ungroupedFieldNames.add(fieldAlias);
            log.warn("Ungrouped field found: {} (alias: {})", queryField.getName(), fieldAlias);
          }
        }
      }
    } else {
      log.error("GROUP BY validation failed: projection is null");
      throw new RuntimeException("Group by error: projection is null");
    }

    if (!ungroupedFieldNames.isEmpty()) {
      String errorMessage = "The fields " + String.join(", ", ungroupedFieldNames) + " has not been grouped or aggregated";
      log.error("GROUP BY validation failed: {}", errorMessage);
      throw new RuntimeException(errorMessage);
    }

    log.debug("GROUP BY validation completed successfully");
  }

  /**
   * 验证连接字段的合法性
   * 包括：连接模型验证、关联字段自动填充等
   *
   * @param modelName 主模型名称
   * @param query 查询对象
   * @throws SqlExecutionException 当连接配置错误时抛出
   */
  private void validateJoinFields(String modelName, Query query) {
    if (query.getJoins() == null) {
      log.debug("No JOIN clauses found, skipping join validation");
      return;
    }

    log.debug("Validating JOIN fields for model: {}, joins count: {}", modelName, query.getJoins().getJoins().size());

    ModelDefinition mainModel = (ModelDefinition) sessionContext.getModelDefinition(modelName);

    for (Query.Join joinConfig : query.getJoins().getJoins()) {
      log.debug("Validating join config: from={}, as={}, localField={}, foreignField={}",
        joinConfig.getFrom(), joinConfig.getAs(), joinConfig.getLocalField(), joinConfig.getForeignField());
      validateJoinConfiguration(joinConfig, mainModel);
    }

    log.debug("JOIN validation completed successfully");
  }

  /**
   * 验证单个连接配置
   *
   * @param joinConfig 连接配置
   * @param mainModel 主模型定义
   * @throws SqlExecutionException 当连接配置错误时抛出
   */
  private void validateJoinConfiguration(Query.Join joinConfig, ModelDefinition mainModel) {
    if (joinConfig.getFrom() == null) {
      log.error("Join validation failed: from model is null");
      throw new SqlExecutionException("Join from model must not be null");
    }

    if (mainModel instanceof EntityDefinition entity &&
        joinConfig.getLocalField() == null &&
        joinConfig.getForeignField() == null) {
      // 自动填充关联字段
      log.debug("Auto-filling relation fields for join: {}", joinConfig.getFrom());
      autoFillRelationFields(joinConfig, entity);
    } else {
      // 验证手动配置的连接字段
      log.debug("Validating manual join fields");
      validateManualJoinFields(joinConfig);
    }

    // 添加别名模型映射
    sessionContext.addAliasModelIfPresent(
      joinConfig.getAs(),
      (ModelDefinition) sessionContext.getModelDefinition(joinConfig.getFrom())
    );

    log.debug("Join configuration validated successfully: {}", joinConfig.getFrom());
  }

  /**
   * 自动填充关联字段
   *
   * @param joinConfig 连接配置
   * @param entity 实体定义
   */
  private void autoFillRelationFields(Query.Join joinConfig, EntityDefinition entity) {
    try {
      ModelRefField relationField = findRelationField(entity, joinConfig)
        .orElseThrow(() -> new SqlExecutionException("Relation field not found for model: " + joinConfig.getFrom()));

      if (relationField.isConditionRelation()) {
        return;
      }

      String localFieldName = relationField.getLocalField() != null ?
        relationField.getLocalField() :
        entity.findIdField().map(TypedField::getName).orElseThrow();

      joinConfig.setLocalField(localFieldName);
      joinConfig.setForeignField(relationField.getForeignField());

      log.debug("Auto-filled relation fields: localField={}, foreignField={}",
        localFieldName, relationField.getForeignField());
    } catch (Exception e) {
      log.error("Failed to auto-fill relation fields for join: {}, error: {}", joinConfig.getFrom(), e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 解析 join 对应的关联字段。同一目标模型允许存在多个关联字段，按以下顺序消歧：
   * <ol>
   *   <li>join 别名命中关联字段名</li>
   *   <li>join 显式声明的键字段与候选的键字段匹配</li>
   *   <li>候选的关联定义完全一致（外键、过滤条件、基数均相同）时任取其一</li>
   * </ol>
   * 仍无法唯一确定时抛出异常，提示调用方改用关联字段名作为 join 别名。
   *
   * @param entity     关联所属的实体定义
   * @param joinConfig 连接配置
   * @return 匹配到的关联字段，找不到时为 {@link Optional#empty()}
   */
  protected Optional<ModelRefField> findRelationField(EntityDefinition entity, Query.Join joinConfig) {
    if (entity.getField(joinConfig.getAs()) instanceof ModelRefField relationField
      && relationField.getFrom().equals(joinConfig.getFrom())) {
      return Optional.of(relationField);
    }
    List<ModelRefField> candidates = entity.getFields().stream()
      .filter(ModelRefField.class::isInstance)
      .map(ModelRefField.class::cast)
      .filter(field -> field.getFrom().equals(joinConfig.getFrom()))
      .toList();
    if (candidates.size() <= 1) {
      return candidates.stream().findFirst();
    }

    if (joinConfig.getLocalField() != null || joinConfig.getForeignField() != null) {
      List<ModelRefField> keyMatched = candidates.stream()
        .filter(field -> matchesJoinKeyFields(field, joinConfig))
        .toList();
      if (keyMatched.size() == 1) {
        return Optional.of(keyMatched.getFirst());
      }
      if (!keyMatched.isEmpty()) {
        candidates = keyMatched;
      }
    }

    ModelRefField first = candidates.getFirst();
    if (candidates.stream().allMatch(field -> isSameRelationDefinition(first, field))) {
      log.debug("Multiple equivalent relations to model {}; using relation field: {}",
        joinConfig.getFrom(), first.getName());
      return Optional.of(first);
    }

    throw new IllegalArgumentException("Ambiguous relation to model " + joinConfig.getFrom()
      + "; use one of the relation field names as join alias: "
      + candidates.stream().map(ModelRefField::getName).collect(Collectors.joining(", ")));
  }

  /**
   * 判断关联字段的键字段是否与 join 显式声明的键字段匹配，join 未声明的键字段不参与比较。
   *
   * @param relationField 候选关联字段
   * @param joinConfig    连接配置
   * @return 是否匹配
   */
  private boolean matchesJoinKeyFields(ModelRefField relationField, Query.Join joinConfig) {
    boolean localMatches = joinConfig.getLocalField() == null
      || Objects.equals(relationField.getLocalField(), joinConfig.getLocalField());
    boolean foreignMatches = joinConfig.getForeignField() == null
      || Objects.equals(relationField.getForeignField(), joinConfig.getForeignField());
    return localMatches && foreignMatches;
  }

  /**
   * 判断两个关联字段的关联定义是否一致，仅字段名不同。
   *
   * @param left  关联字段
   * @param right 关联字段
   * @return 关联定义是否完全一致
   */
  private boolean isSameRelationDefinition(ModelRefField left, ModelRefField right) {
    return left.isMultiple() == right.isMultiple()
      && left.getStrategy() == right.getStrategy()
      && Objects.equals(left.getLocalField(), right.getLocalField())
      && Objects.equals(left.getForeignField(), right.getForeignField())
      && Objects.equals(left.getFilter(), right.getFilter());
  }

  /**
   * 验证手动配置的连接字段
   *
   * @param joinConfig 连接配置
   * @throws SqlExecutionException 当字段配置错误时抛出
   */
  private void validateManualJoinFields(Query.Join joinConfig) {
    if (joinConfig.getLocalField() == null || joinConfig.getForeignField() == null) {
      log.error("Manual join validation failed: localField={}, foreignField={}",
        joinConfig.getLocalField(), joinConfig.getForeignField());
      throw new SqlExecutionException("LocalField and foreignField must not be null when is not association field");
    }
  }

  /**
   * 查找查询中的关联字段
   *
   * @param model 模型定义
   * @param query 查询对象
   * @return 关联字段映射，key为字段别名，value为关联字段定义
   */
  public Map<String, ModelRefField> findRelationFields(ModelDefinition model, Query query) {
    log.debug("Finding relation fields for model: {}, hasProjection: {}, hasExpand: {}",
      model.getName(), hasProjectionFields(query), query != null && query.hasExpand());

    Map<String, ModelRefField> relationFieldMap = new HashMap<>();

    if (!(model instanceof EntityDefinition entity)) {
      log.debug("Model is not EntityDefinition, returning empty relation field map");
      return relationFieldMap;
    }

    // 优先按 expand 列表过滤关联字段
    if (query != null && query.hasExpand()) {
      findRelationFieldsFromExpand(entity, query.getExpand(), relationFieldMap);
    } else if (hasProjectionFields(query)) {
      findRelationFieldsFromProjection(entity, query, relationFieldMap);
    } else {
      findAllRelationFields(entity, relationFieldMap);
    }

    log.debug("Found {} relation fields: {}", relationFieldMap.size(), relationFieldMap.keySet());
    return relationFieldMap;
  }

  /**
   * 从 expand 列表中查找关联字段
   *
   * @param entity 实体定义
   * @param expand expand 字段列表
   * @param relationFieldMap 关联字段映射
   */
  private void findRelationFieldsFromExpand(EntityDefinition entity, List<String> expand, Map<String, ModelRefField> relationFieldMap) {
    log.debug("Finding relation fields from expand list: {}", expand);

    for (String expandPath : expand) {
      // 取顶层字段名（classId.teacher -> classId）
      String topField = expandPath.contains(".") ? expandPath.split("\\.")[0] : expandPath;
      entity.getFields().stream()
        .filter(f -> f.getName().equals(topField) && f instanceof ModelRefField)
        .map(f -> (ModelRefField) f)
        .findFirst()
        .ifPresent(relationField -> {
          relationFieldMap.put(topField, relationField);
          log.debug("Found relation field from expand: {}", topField);
        });
    }
  }

  /**
   * 提取指定关联字段的子级 expand 路径
   * 例如 expand=["classId.teacher", "classId.students.name"] 且 relationFieldName="classId"
   * 返回 ["teacher", "students.name"]
   *
   * @param expand 当前层级的 expand 列表
   * @param relationFieldName 关联字段名
   * @return 子级 expand 列表，无子级时返回 null
   */
  public static List<String> extractChildExpand(List<String> expand, String relationFieldName) {
    if (expand == null || expand.isEmpty()) {
      return null;
    }
    String prefix = relationFieldName + ".";
    List<String> childExpand = expand.stream()
      .filter(path -> path.startsWith(prefix))
      .map(path -> path.substring(prefix.length()))
      .filter(path -> !path.isEmpty())
      .toList();
    return childExpand.isEmpty() ? null : childExpand;
  }

  /**
   * 检查查询是否有投影字段
   *
   * @param query 查询对象
   * @return 是否有投影字段
   */
  private boolean hasProjectionFields(Query query) {
    return query != null &&
           query.getProjection() != null &&
           !query.getProjection().getFields().isEmpty();
  }

  /**
   * 从投影字段中查找关联字段
   *
   * @param entity 实体定义
   * @param query 查询对象
   * @param relationFieldMap 关联字段映射
   */
  private void findRelationFieldsFromProjection(EntityDefinition entity, Query query, Map<String, ModelRefField> relationFieldMap) {
    log.debug("Finding relation fields from projection, projection fields count: {}",
      query.getProjection().getFields().size());

    for (Map.Entry<String, Query.QueryCall> entry : query.getProjection().getFields().entrySet()) {
      String fieldAlias = entry.getKey();
      Query.QueryCall fieldExpression = entry.getValue();

      if (fieldExpression instanceof Query.QueryField queryField) {
        log.debug("Checking field: {} (alias: {})", queryField.getName(), fieldAlias);

        entity.getFields().stream()
          .filter(field -> field.getName().equals(queryField.getName()) && field instanceof ModelRefField)
          .map(field -> (ModelRefField) field)
          .findFirst()
          .ifPresent(relationField -> {
            relationFieldMap.put(fieldAlias, relationField);
            log.debug("Found relation field: {} -> {}", fieldAlias, relationField.getName());
          });
      }
    }
  }

  /**
   * 查找所有关联字段
   *
   * @param entity 实体定义
   * @param relationFieldMap 关联字段映射
   */
  private void findAllRelationFields(EntityDefinition entity, Map<String, ModelRefField> relationFieldMap) {
    log.debug("Finding all relation fields for entity: {}", entity.getName());

    for (Field field : entity.getFields()) {
      if (field instanceof ModelRefField relationField) {
        relationFieldMap.put(relationField.getName(), relationField);
        log.debug("Found relation field: {}", relationField.getName());
      }
    }
  }

  /**
   * 查找关联数据列表
   *
   * @param relationQueryFunction 关联查询函数
   * @param relationField 关联字段定义
   * @param foreignKeyValues 外键值集合
   * @return 关联数据列表
   */
  private List<Map<String, Object>> findRelationDataList(
    BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
    ModelRefField relationField,
    Set<Object> foreignKeyValues) {

    log.debug("Finding relation data for field: {}, foreign key values count: {}",
      relationField.getName(), foreignKeyValues.size());

    Query relationQuery = new Query();
    Map<String, Object> targetFilter =
      RelationFilterSupport.resolveTargetFilter(relationField.getFilter(), Map.of(), relationField.getName());
    if (targetFilter.isEmpty()) {
      relationQuery.setFilter(field(relationField.getForeignField()).in(foreignKeyValues).toJsonString());
    } else {
      Map<String, Object> keyFilter = Map.of(
        relationField.getForeignField(),
        Map.of("_in", foreignKeyValues)
      );
      Map<String, Object> combinedFilter = Map.of("_and", List.of(keyFilter, targetFilter));
      relationQuery.setFilter(JsonUtils.toJsonString(combinedFilter));
    }

    List<Map<String, Object>> result = relationQueryFunction.apply(relationField.getFrom(), relationQuery);

    log.debug("Found {} relation data records for field: {}", result.size(), relationField.getName());
    return result;
  }

  /**
   * 执行嵌套查询
   *
   * @param parentDataList 父级数据列表
   * @param relationQueryFunction 关联查询函数
   * @param model 模型定义
   * @param query 查询对象
   * @param maxDepth 最大嵌套深度
   */
  public void nestedQuery(List<Map<String, Object>> parentDataList,
                          BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                          ModelDefinition model,
                          Query query,
                          int maxDepth) {
    log.debug("Starting nested query for model: {}, parent data count: {}, max depth: {}",
      model.getName(), parentDataList.size(), maxDepth);

    try {
      nestedQuery(parentDataList, relationQueryFunction, model, query, new AtomicInteger(maxDepth));
      log.debug("Nested query completed successfully for model: {}", model.getName());
    } catch (Exception e) {
      log.error("Nested query failed for model: {}, error: {}", model.getName(), e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 执行嵌套查询（私有方法，使用原子整数控制深度）
   *
   * @param parentDataList 父级数据列表
   * @param relationQueryFunction 关联查询函数
   * @param model 模型定义
   * @param query 查询对象
   * @param remainingDepth 剩余深度（原子整数）
   */
  private void nestedQuery(List<Map<String, Object>> parentDataList,
                           BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                           ModelDefinition model,
                           Query query,
                           AtomicInteger remainingDepth) {
    if (remainingDepth.get() <= 0) {
      log.debug("Nested query depth limit reached for model: {}", model.getName());
      return;
    }

    log.debug("Processing nested query for model: {}, remaining depth: {}", model.getName(), remainingDepth.get());

    Map<String, ModelRefField> relationFieldMap = findRelationFields(model, query);

    relationFieldMap.entrySet().stream().forEach(entry -> {
      String relationFieldAlias = entry.getKey();
      ModelRefField relationField = entry.getValue();

      log.debug("Processing relation field: {} (alias: {})", relationField.getName(), relationFieldAlias);
      processRelationField(parentDataList, relationQueryFunction, model, query,
        remainingDepth, relationFieldAlias, relationField);
    });
  }

  /**
   * 处理单个关联字段
   *
   * @param parentDataList 父级数据列表
   * @param relationQueryFunction 关联查询函数
   * @param model 模型定义
   * @param query 查询对象
   * @param remainingDepth 剩余深度
   * @param relationFieldAlias 关联字段别名
   * @param relationField 关联字段定义
   */
  private void processRelationField(List<Map<String, Object>> parentDataList,
                                    BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                                    ModelDefinition model,
                                    Query query,
                                    AtomicInteger remainingDepth,
                                    String relationFieldAlias,
                                    ModelRefField relationField) {
    if (relationField.isConditionRelation()
      || (relationField.getFilter() != null && !relationField.getFilter().isEmpty())) {
      remainingDepth.decrementAndGet();
      fillConditionRelationData(parentDataList, relationQueryFunction, query,
        remainingDepth, relationFieldAlias, relationField);
      return;
    }

    // 收集所有外键值
    Set<Object> foreignKeyValues = parentDataList.stream()
      .map(dataItem -> dataItem.get(relationField.getLocalField()))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    log.debug("Collected {} foreign key values for relation field: {}",
      foreignKeyValues.size(), relationField.getName());

    EntityDefinition relationModel = (EntityDefinition) sessionContext.getModelDefinition(relationField.getFrom());

    if (relationModel == null || relationModel.getField(relationField.getForeignField()) == null) {
      log.warn("Relation model or foreign field not found for relation: {}", relationField.getName());
      return;
    }

    // 查询关联数据并按外键分组
    Map<Object, List<Map<String, Object>>> relationDataGroup = findRelationDataList(relationQueryFunction, relationField, foreignKeyValues)
      .stream()
      .collect(Collectors.groupingBy(dataItem -> dataItem.get(relationField.getForeignField())));

    log.debug("Grouped relation data by foreign key, groups count: {}", relationDataGroup.size());

    // 递减深度（每个关联字段层级只递减一次，避免在循环内按数据条数递减）
    remainingDepth.decrementAndGet();

    // 填充关联数据到父级数据中
    parentDataList.forEach(parentDataItem -> {
      if (model instanceof EntityDefinition) {
        fillRelationDataToParent(parentDataItem, relationField, relationFieldAlias,
          relationDataGroup, relationQueryFunction, remainingDepth, relationModel, query);
      }
    });
  }

  /**
   * Fill a condition relation, or a key relation whose filter references source fields.
   */
  private void fillConditionRelationData(List<Map<String, Object>> parentDataList,
                                         BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                                         Query query,
                                         AtomicInteger remainingDepth,
                                         String relationFieldAlias,
                                         ModelRefField relationField) {
    EntityDefinition relationModel = (EntityDefinition) sessionContext.getModelDefinition(relationField.getFrom());
    if (relationModel == null) {
      log.warn("Relation model not found for relation: {}", relationField.getName());
      return;
    }

    List<String> childExpand = query != null
      ? extractChildExpand(query.getExpand(), relationField.getName())
      : null;
    Query childQuery = null;
    if (childExpand != null) {
      childQuery = new Query();
      childQuery.setExpand(childExpand);
    }

    if (!relationField.isConditionRelation()
      && !RelationFilterSupport.hasSourceReference(relationField.getFilter(), relationField.getName())) {
      fillBatchedKeyRelationData(parentDataList, relationQueryFunction, childQuery,
        remainingDepth, relationFieldAlias, relationField, relationModel);
      return;
    }

    for (Map<String, Object> parentDataItem : parentDataList) {
      Map<String, Object> targetFilter =
        RelationFilterSupport.resolveTargetFilter(
          relationField.getFilter(),
          parentDataItem,
          relationField.getName()
        );

      if (!relationField.isConditionRelation()) {
        Object localKeyValue = getSourceValue(parentDataItem, relationField.getLocalField());
        if (localKeyValue == null) {
          parentDataItem.put(relationFieldAlias, relationField.isMultiple() ? List.of() : null);
          continue;
        }
        Map<String, Object> keyFilter = Map.of(
          relationField.getForeignField(),
          Map.of("_eq", localKeyValue)
        );
        targetFilter = targetFilter.isEmpty()
          ? keyFilter
          : Map.of("_and", List.of(keyFilter, targetFilter));
      }

      if (targetFilter.isEmpty()) {
        parentDataItem.put(relationFieldAlias, relationField.isMultiple() ? List.of() : null);
        continue;
      }

      Query relationQuery = new Query();
      relationQuery.setFilter(JsonUtils.toJsonString(targetFilter));
      List<Map<String, Object>> relationDataList =
        relationQueryFunction.apply(relationField.getFrom(), relationQuery);

      if (childQuery != null) {
        nestedQuery(relationDataList, relationQueryFunction, relationModel, childQuery,
          new AtomicInteger(remainingDepth.get()));
      }

      if (!relationField.isMultiple() && relationDataList.size() > 1) {
        throw new IllegalStateException(
          "Relation " + relationField.getName() + " matched more than one record");
      }
      parentDataItem.put(relationFieldAlias, relationField.isMultiple()
        ? relationDataList
        : relationDataList.isEmpty() ? null : relationDataList.getFirst());
    }
  }

  private void fillBatchedKeyRelationData(List<Map<String, Object>> parentDataList,
                                          BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                                          Query childQuery,
                                          AtomicInteger remainingDepth,
                                          String relationFieldAlias,
                                          ModelRefField relationField,
                                          EntityDefinition relationModel) {
    Map<Object, List<Map<String, Object>>> relationDataGroup = new LinkedHashMap<>();
    Set<Object> foreignKeyValues = new LinkedHashSet<>();
    for (Map<String, Object> parentDataItem : parentDataList) {
      Object localKeyValue = getSourceValue(parentDataItem, relationField.getLocalField());
      if (localKeyValue != null) {
        foreignKeyValues.add(localKeyValue);
      }
    }
    if (foreignKeyValues.isEmpty()) {
      parentDataList.forEach(parentDataItem ->
        parentDataItem.put(relationFieldAlias, relationField.isMultiple() ? List.of() : null));
      return;
    }

    Map<String, Object> targetFilter = RelationFilterSupport.resolveTargetFilter(
      relationField.getFilter(), Map.of(), relationField.getName());
    Map<String, Object> keyCondition = new LinkedHashMap<>();
    keyCondition.put("_in", foreignKeyValues);
    Map<String, Object> keyFilter = new LinkedHashMap<>();
    keyFilter.put(relationField.getForeignField(), keyCondition);
    Map<String, Object> combinedFilter = new LinkedHashMap<>();
    combinedFilter.put("_and", List.of(keyFilter, targetFilter));

    Query relationQuery = new Query();
    relationQuery.setFilter(JsonUtils.toJsonString(combinedFilter));
    List<Map<String, Object>> relationDataList =
      relationQueryFunction.apply(relationField.getFrom(), relationQuery);
    if (childQuery != null) {
      nestedQuery(relationDataList, relationQueryFunction, relationModel, childQuery,
        new AtomicInteger(remainingDepth.get()));
    }
    relationDataList.forEach(dataItem ->
      relationDataGroup.computeIfAbsent(dataItem.get(relationField.getForeignField()), key -> new ArrayList<>())
        .add(dataItem));

    for (Map<String, Object> parentDataItem : parentDataList) {
      Object localKeyValue = getSourceValue(parentDataItem, relationField.getLocalField());
      List<Map<String, Object>> matches = localKeyValue == null
        ? List.of()
        : relationDataGroup.getOrDefault(localKeyValue, List.of());
      if (!relationField.isMultiple() && matches.size() > 1) {
        throw new IllegalStateException(
          "Relation " + relationField.getName() + " matched more than one record");
      }
      parentDataItem.put(relationFieldAlias, relationField.isMultiple()
        ? matches
        : matches.isEmpty() ? null : matches.getFirst());
    }
  }

  private Object getSourceValue(Map<String, Object> data, String fieldName) {
    if (data.containsKey(fieldName)) {
      return data.get(fieldName);
    }
    String camelCaseField = underscoreToCamelCase(fieldName);
    return camelCaseField.equals(fieldName) ? null : data.get(camelCaseField);
  }

  private String underscoreToCamelCase(String fieldName) {
    if (fieldName == null || fieldName.isEmpty()) {
      return fieldName;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = false;
    for (char character : fieldName.toCharArray()) {
      if (character == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(character));
        capitalizeNext = false;
      } else {
        result.append(Character.toLowerCase(character));
      }
    }
    return result.toString();
  }
  /**
   * 将关联数据填充到父级数据中
   *
   * @param parentDataItem 父级数据项
   * @param relationField 关联字段定义
   * @param relationFieldAlias 关联字段别名
   * @param relationDataGroup 关联数据分组
   * @param relationQueryFunction 关联查询函数
   * @param remainingDepth 剩余深度
   * @param relationModel 关联模型定义
   */
  private void fillRelationDataToParent(Map<String, Object> parentDataItem,
                                        ModelRefField relationField,
                                        String relationFieldAlias,
                                        Map<Object, List<Map<String, Object>>> relationDataGroup,
                                        BiFunction<String, Query, List<Map<String, Object>>> relationQueryFunction,
                                        AtomicInteger remainingDepth,
                                        EntityDefinition relationModel,
                                        Query query) {
    Object localKeyValue = parentDataItem.get(relationField.getLocalField());

    if (localKeyValue == null) {
      log.debug("Local key value is null for relation field: {}", relationField.getName());
      parentDataItem.put(relationFieldAlias, relationField.isMultiple() ? List.of() : null);
      return;
    }

    List<Map<String, Object>> relationDataList = relationDataGroup.getOrDefault(localKeyValue, List.of());

    log.debug("Found {} relation data records for local key: {}", relationDataList.size(), localKeyValue);

    // 递归处理嵌套查询，传递子级 expand 路径
    // 使用深度快照避免多条父数据间互相消耗深度
    List<String> childExpand = query != null
      ? extractChildExpand(query.getExpand(), relationField.getName())
      : null;
    Query childQuery = null;
    if (childExpand != null) {
      childQuery = new Query();
      childQuery.setExpand(childExpand);
    }
    nestedQuery(relationDataList, relationQueryFunction, relationModel, childQuery, new AtomicInteger(remainingDepth.get()));

    // 根据关联类型设置值
    Object relationValue = relationField.isMultiple() ?
      relationDataList :
      (!relationDataList.isEmpty() ? relationDataList.getFirst() : null);

    parentDataItem.put(relationFieldAlias, relationValue);

    log.debug("Set relation value for field: {} (alias: {}), value type: {}",
      relationField.getName(), relationFieldAlias,
      relationValue != null ? relationValue.getClass().getSimpleName() : "null");
  }

  /**
   * 生成字段值，包括类型转换、默认值处理、自动生成值等
   *
   * @param modelName 模型名称
   * @param inputData 输入数据
   * @param isUpdate 是否为更新操作
   * @return 处理后的数据
   */
  protected Map<String, Object> generateFieldValues(String modelName, Map<String, Object> inputData, boolean isUpdate) {
    log.debug("Generating field values for model: {}, input data size: {}, isUpdate: {}",
      modelName, inputData.size(), isUpdate);

    EntityDefinition entity = (EntityDefinition) sessionContext.getModelDefinition(modelName);
    List<TypedField<?, ?>> entityFields = entity.getFields();
    Map<String, Object> processedData = new HashMap<>();

    // 处理输入数据的类型转换
    processInputData(inputData, entity, processedData);

    // 处理默认值和自动生成值
    processDefaultAndGeneratedValues(entityFields, processedData, isUpdate);

    log.debug("Field value generation completed for model: {}, processed data size: {}",
      modelName, processedData.size());
    return processedData;
  }

  /**
   * 处理输入数据的类型转换
   *
   * @param inputData 输入数据
   * @param entity 实体定义
   * @param processedData 处理后的数据
   */
  private void processInputData(Map<String, Object> inputData, EntityDefinition entity, Map<String, Object> processedData) {
    log.debug("Processing input data for entity: {}, input fields: {}", entity.getName(), inputData.keySet());

    inputData.forEach((fieldName, fieldValue) -> {
      TypedField<?, ?> field = entity.getField(fieldName);
      if (field != null && !(field instanceof ModelRefField)) {
        Object convertedValue = convertParameter(field, fieldValue);
        processedData.put(field.getName(), convertedValue);
        log.debug("Converted field: {} = {} -> {}", fieldName, fieldValue, convertedValue);
      } else {
        log.debug("Skipped field: {} (not found or is relation field)", fieldName);
      }
    });
  }

  /**
   * 处理默认值和自动生成值
   *
   * @param entityFields 实体字段列表
   * @param processedData 处理后的数据
   * @param isUpdate 是否为更新操作
   */
  private void processDefaultAndGeneratedValues(List<TypedField<?, ?>> entityFields,
                                                Map<String, Object> processedData,
                                                boolean isUpdate) {
    log.debug("Processing default and generated values for {} fields", entityFields.size());

    for (TypedField<?, ?> field : entityFields) {
      if (field instanceof ModelRefField) {
        continue;
      }

      Object currentValue = processedData.get(field.getName());
      DefaultValue defaultValue = field.getDefaultValue();
      if (defaultValue != null) {
        Object generatedValue = generateFieldValue(field, currentValue, isUpdate);
        processedData.put(field.getName(), generatedValue);

        if (!Objects.equals(currentValue, generatedValue)) {
          log.debug("Generated value for field: {} = {} -> {}",
            field.getName(), currentValue, generatedValue);
        }
      }
    }
  }

  /**
   * 转换参数类型
   *
   * @param field 字段定义
   * @param value 原始值
   * @return 转换后的值
   */
  protected Object convertParameter(TypedField<?, ?> field, Object value) {
    try {
      Object convertedValue = sessionContext.getTypeHandlerMap().get(field.getType())
        .convertParameter(field, value);

      log.debug("Converted parameter: field={}, type={}, value={} -> {}",
        field.getName(), field.getType(), value, convertedValue);

      return convertedValue;
    } catch (Exception e) {
      log.error("Failed to convert parameter: field={}, type={}, value={}, error: {}",
        field.getName(), field.getType(), value, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 生成字段值，包括自动生成ID、时间戳等
   *
   * @param field 字段定义
   * @param currentValue 当前值
   * @param isUpdate 是否为更新操作
   * @return 生成的值
   */
  protected Object generateFieldValue(TypedField<?, ?> field, Object currentValue, boolean isUpdate) {
    if (currentValue != null) {
      return currentValue;
    }

    DefaultValue defaultValue = field.getDefaultValue();
    if (defaultValue == null) {
      return null;
    }

    if (defaultValue.isGenerated()) {
      String generatedName = defaultValue.getName();
      if ("uuid".equals(generatedName)) {
        String uuid = UUID.randomUUID().toString();
        log.debug("Generated UUID for field: {} = {}", field.getName(), uuid);
        return uuid;
      } else if ("now".equals(generatedName)) {
        Object timeValue = generateCurrentTimeValue(field);
        log.debug("Generated current time for field: {} = {}", field.getName(), timeValue);
        return timeValue;
      } else {
        // 忽略其他生成值类型
        log.debug("Ignored generated value type for field: {} = {}", field.getName(), generatedName);
        return null;
      }
    } else if (defaultValue.isFixed()) {
      Object convertedDefault = convertParameter(field, defaultValue.getValue());
      log.debug("Used default value for field: {} = {}", field.getName(), convertedDefault);
      return convertedDefault;
    }

    return null;
  }

  /**
   * 生成当前时间值
   *
   * @param field 字段定义
   * @return 当前时间值
   */
  private Object generateCurrentTimeValue(TypedField<?, ?> field) {
    if (field instanceof DateTimeField) {
      return LocalDateTime.now();
    } else if (field instanceof DateField) {
      return LocalDate.now();
    } else if (field instanceof TimeField) {
      return LocalTime.now();
    }
    return null;
  }

  /**
   * 插入关联记录
   *
   * @param modelName 模型名称
   * @param relationObject 关联对象
   * @param parentId 父级ID
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  protected void insertRelatedRecords(String modelName, Map<String, Object> relationObject, Object parentId) {
    log.debug("Inserting relation records for model: {}, parentId: {}", modelName, parentId);
    try {
      EntityDefinition entity = (EntityDefinition) sessionContext.getModelDefinition(modelName);
      relationObject.forEach((fieldName, fieldValue) -> {
        if (fieldValue != null && entity.getField(fieldName) instanceof ModelRefField relationField) {
          if (relationField.isConditionRelation()) {
            return;
          }
          processRelationFieldInsertion(entity, fieldName, fieldValue, relationObject.get(relationField.getLocalField()));
        }
      });
      log.debug("Relation record insertion completed for model: {}", modelName);
    } catch (Exception e) {
      log.error("Failed to insert relation records for model: {}, parentId: {}, error: {}",
        modelName, parentId, e.getMessage(), e);
      throw e;
    }
  }

  /**
   * 处理关联字段的插入
   *
   * @param entity 实体定义
   * @param fieldName 字段名称
   * @param fieldValue 字段值
   * @param parentId 父级ID
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private void processRelationFieldInsertion(EntityDefinition entity, String fieldName, Object fieldValue, Object parentId) {
    if (!(entity.getField(fieldName) instanceof ModelRefField relationField)) {
      return;
    }
    log.debug("Processing relation field insertion: field={}, relationField={}, parentId={}", fieldName, relationField.getName(), parentId);
    if (relationField.isMultiple()) {
      // 处理一对多关联
      Collection<?> relationCollection = (Collection) fieldValue;
      log.debug("Processing one-to-many relation: {} items", relationCollection.size());

      relationCollection.forEach(relationItem -> {
        Map<String, Object> relationRecord = JsonUtils.convertValue(relationItem, Map.class);
        relationRecord.put(relationField.getForeignField(), parentId);

        log.debug("Inserting relation record: {} -> {}", relationField.getFrom(), relationRecord);
        getDataService().insert(relationField.getFrom(), relationRecord);
      });
    } else {
      // 处理一对一关联
      Map<String, Object> relationRecord = JsonUtils.convertValue(fieldValue, Map.class);
      relationRecord.put(relationField.getForeignField(), parentId);

      log.debug("Inserting relation record: {} -> {}", relationField.getFrom(), relationRecord);
      getDataService().insert(relationField.getFrom(), relationRecord);
    }
  }
}
