# feat-011: Vector — 向量字段类型与语义搜索

## Context

Flexmodel 定位是"统一数据访问层"，数据 CRUD 通过 `EventAwareDataService` 执行。新增向量能力后，数据从"能存能查"升级到"能懂能联想"。

**核心设计决策**：向量是一个**字段类型**，和 `String`、`Int` 没有本质区别。它存于用户表中，走 DataService 管理生命周期。独立的搜索加速层（VectorSearchEngine）只在数据量大时才需要。

**关键架构约束**：

- 向量搜索**融入现有查询体系**，不是独立世界——用户做"结构化过滤 + 语义排序"是一个请求
- 写入路径**不能被外部嵌入 API 卡死**——提供同步/异步两种嵌入模式
- 向量有**版本和归属**——embedding model 变更触发 reindex，collection 带项目维度

---

## 业务价值

向量能力让用户声明一个 `Vector` 类型字段后，就自动拥有以下能力：

### 语义搜索 / RAG

按意思搜索文档、知识库、工单，不依赖关键词。给大模型提供基于实际业务数据的上下文（RAG），让 AI 回答有依据而不是凭空生成。

### 智能推荐

"和这条相似的记录"——相关文章、相似产品、关联工单。不需要手动维护标签分类，向量自动算相似度，冷启动也能推荐。

### 数据去重 / 查重

提交的内容是否和已有内容语义重复？论文查重、工单去重、投诉合并，比关键词匹配更准。

### 内容分类 / 自动标签

新数据进来，向量最接近哪个类别的中心点就自动归类。

### 异常检测

向量距离异常远的记录可能是异常数据——日志异常、交易欺诈、离群内容检测。

---

## 架构

### 三层模型

```
┌──────────────────────────────────────────────────────┐
│ Layer 1: 字段层（所有数据库通用）                       │
│   Vector 字段类型 → DataService insert/update/delete   │
│   不需要任何新接口                                      │
├──────────────────────────────────────────────────────┤
│ Layer 2: 嵌入层                                       │
│   @embedding 注解 → 自动调用 EmbeddingProvider 填值     │
│   EmbeddingProvider 接口（文本 → 向量）                 │
│   同步模式 / 异步模式 可选                              │
├──────────────────────────────────────────────────────┤
│ Layer 3: 搜索加速层（默认 InMemory，数据量大时可替换）   │
│   VectorSearchEngine 接口（upsert / delete / search）  │
│   内置 InMemory 实现（< 50K 够用）                     │
│   可替换为 PGVector / Milvus                           │
│   搜索融入 Query 对象，不独立端点                       │
└──────────────────────────────────────────────────────┘
```

### 写入线

#### 同步嵌入模式（默认，小数据量 + 实时搜索需求）

```
CRUD → EventAwareDataService.insert/update
  ↓ 触发 PreInsertEvent / PreUpdateEvent（onPreChange，事务内、写入前）
  ↓ VectorEmbeddingProcessor.onPreChange(PreChangeEvent)
  ↓ 检查目标模型是否有 VectorField + @embedding 配置
  ↓ 有 → 检查 source 文本是否变化（比对 event.getOldData() 与 getNewData()）
  ↓ 变化 → EmbeddingProvider.embed(拼接文本) → 通过 event.setNewData() 回填 Vector 字段
  ↓
  ↓ 实际 SQL 写入（此时 newData 已含向量）
  ↓
  ↓ 触发 InsertedEvent / UpdatedEvent（onChanged，写入成功后）
  ↓ VectorSyncListener.onChanged(ChangedEvent)
  ↓ 如果有 VectorSearchEngine 配置
  ↓ → VectorSearchEngine.upsert(collection, id, vector, metadata)
```

**关键事实（基于代码）**：`PreChangeEvent` 提供 `getNewData()` / `setNewData()` / `getOldData()`，监听器**可以在写入前修改待持久化的数据**。`PreUpdateEvent` 构造时携带 `oldData` 和 `newData`，source 变化检测可行（前提：`EventAwareDataService` 在 update 路径实际填充了 oldData——实现时需验证，若未填充则回退为"每次 update 都重新 embed"）。

#### 异步嵌入模式（大数据量 / 嵌入 API 不稳定场景）

```
CRUD → EventAwareDataService.insert/update
  ↓ VectorEmbeddingProcessor.onPreChange
  ↓ 检查字段是否有 @embedding 配置
  ↓ 有 → Vector 字段暂时存 null → 正常写入（setNewData 不改 Vector 字段）
  ↓
  ↓ 异步队列 / 定时任务（VectorReindexService）
  ↓ 扫描 Vector 字段为 null 的记录
  ↓ EmbeddingProvider.embed → 回填 Vector 字段 → 同步到 VectorSearchEngine
```

用户通过配置选择模式：

```properties
flexmodel.vector.embedding.mode=sync    # 默认，写入时同步嵌入
# flexmodel.vector.embedding.mode=async  # 写入不阻塞，后台异步补填
```

**source 文本变化检测**：更新时，如果 @embedding 注解的 source 字段值与上次嵌入时相同（通过比对 `oldData` 和 `newData` 中对应字段），跳过 EmbeddingProvider 调用——减少 API 费用和延迟。

**嵌入失败处理**：
- 同步模式：嵌入 API 失败 → `onPreChange` 抛 `VectorException`，由于在事务内、写入前抛出，整个 insert/update 回滚
- 异步模式：嵌入 API 失败 → 记录进入 retry 队列，不阻塞数据写入

### 搜索线

向量搜索**融入现有查询体系**，但通过一个**专用的搜索端点**承载（现有 `GET .../records` 是分页查询，参数走 query string，无法承载复杂的 similarity 子句；现有 `POST .../records` 是创建记录。因此新增 `POST .../records/search`）：

```
POST /projects/{projectId}/models/{modelName}/records/search
  body: VectorSearchRequest {
    filter: "{\"status\": {\"_eq\": \"open\"}}",   # 结构化过滤（与现有 filter 字符串格式一致）
    similarity: {                                    # 向量搜索子句
      field: "embedding",                            # 搜索哪个 Vector 字段
      query: "设备故障报警",                           # 文本语义搜索
      # OR
      similarTo: "record-123",                       # 以记录找记录
      topK: 5,
      threshold: 0.7
    }
  }
  → 返回 VectorSearchResult {
       list: [{id, similarityScore, source, ...其他字段}],  # 完整记录 + 相似度 + source原文
       total: 5
     }
```

**为什么是独立搜索端点而非融入 GET findAll**：
1. 现有 `RecordResource.findPagingRecords` 是 `GET`，参数（filter/sort/expand）全走 query string，`DataService.findPagingRecords(...)` 接收原始参数而非 `Query` 对象。similarity 子句是嵌套结构，不适合放 query string。
2. 现有 `POST .../records` 语义是创建记录，复用会造成歧义。
3. 新增 `POST .../records/search` 语义清晰，body 可承载结构化请求，不破坏现有 CRUD 端点契约。

**执行流程**（`VectorSearchService.search()`）：
1. 解析 `VectorSearchRequest.similarity` 子句
2. 获取 queryVector：
   - `query` 模式 → `EmbeddingProvider.embed(query)` 生成查询向量
   - `similarTo` 模式 → 从 VectorSearchEngine 取目标向量；若引擎未命中则 `DataService.findById` 取记录的 Vector 字段值
3. 若 `filter` 非空：先执行结构化过滤（复用 `DataService.findAll(Query)` with filter）拿到候选 ID 集合，再在候选集内做向量搜索；若 filter 为空则直接全量向量搜索
4. 调 `VectorSearchEngine.search()` 获取命中 ID + score
5. 批量 `DataService.findById` 回填完整记录（一次 batch，非 N+1）
6. 返回结果附加 `similarityScore` 字段和 source 字段原文，按 score 降序排列

**query 和 similarTo 互斥**：传 query 是文本搜索，传 similarTo 是以记录找记录。

**搜索结果默认回填完整记录**。`VectorSearchEngine.search()` 返回 `{id, score, metadata}`，`VectorSearchService` 做批量 findById 回填。metadata 中的 source 原文可供 RAG 场景直接使用（无需再回查 DB 取 source 字段）；完整记录回填仅在用户需要 source 以外的字段时进行。

**metadata 定义**：upsert 时 metadata 由系统自动填充，包含 @embedding 的 source 字段原文 + 记录 ID。因为搜索已支持结构化过滤 + 向量排序合为一个请求，不需要 metadata 做额外的元数据过滤。

**相似度排序**：相似度排序在 `VectorSearchService` 内部按 score 降序完成，**不走 `Query.sort`**（`Query.OrderBy.Sort.field` 是 `QueryField`，不支持按计算字段排序）。

### 混合搜索（Hybrid Search）— Layer 3 演进方向

当前实现纯向量搜索。方案预留 hybrid search 扩展点：

```
VectorSearchRequest {
  similarity: {
    field: "embedding",
    query: "数据库连接超时",
    hybrid: true,              # 向量 + 关键词双路召回
    hybridWeight: 0.7,         # 向量权重（0.3 给关键词 BM25）
    ...
  }
}
```

`VectorSearchEngine.search()` 接口的返回类型和排序机制设计时考虑混合权重融合，确保 hybrid 扩展不破坏现有接口。

---

## 配置方式

### FML：向量字段 + @embedding 注解

> **重要**：维度通过 `@dimensions` 注解声明，**不是** `Vector(1536)` 参数化类型语法。
> 原因：FML 语法（`ModelParser.jj` 的 `Type()` 产生式）只支持 `Identifier` + 可选 `[]`，不支持 `Type(args)`。
> 这与现有约定一致——`String` 用 `@length(255)`、`Float` 用 `@precision(20) @scale(2)`，参数化信息走注解而非类型名。

```fml
model Article {
  title : String,
  content : String,

  # 简单用法：source 字段空格拼接 → embed → 自动填入此字段
  embedding : Vector @dimensions(1536) @embedding(source: ["title", "content"])
}
```

```fml
model Article {
  title : String,
  content : String,

  # 精确用法：template 格式化，影响搜索质量
  embedding : Vector @dimensions(1536) @embedding(source: ["title", "content"], template: "{title}: {content}")
}
```

```fml
model Article {
  title : String,
  content : String,
  summary : String,

  # 同一模型支持多个向量字段，各自独立
  title_embedding : Vector @dimensions(256) @embedding(source: ["title"]),
  fulltext_embedding : Vector @dimensions(1536) @embedding(source: ["title", "content"], template: "{title}\n{content}")
}
```

**注解参数说明**：

- `@dimensions(N)` — 必填，向量维度。由 `EmbeddingProvider.getDimensions()` 校验，不匹配则启动时报错。
- `@embedding(source: [...], template: "...")` — 声明该字段的值由嵌入提供商自动生成：
  - `source` — 必填，声明哪些字段的值参与嵌入（列表，语法同 `@index(fields: [...])`，已支持）。
  - `template` — 可选，控制文本拼接格式。不写则 source 字段值用空格拼接。

**metadata** — 不需要用户声明，系统自动填充 source 文本原文 + 记录 ID 到 VectorSearchEngine。

**FML 解析**：
- `Vector` 作为类型名（普通 Identifier），语法层无需改动。
- `@dimensions` 和 `@embedding` 作为字段注解，在 `ASTNodeConverter.toSchemaField()` 的 Vector case 中解析（与 `@length` 在 String case 中解析的模式一致）。
- `@embedding(source: [...])` 的列表参数解析复用现有 `@index(fields: [...])` 的列表解析机制。

### 向量版本追踪

每个 @embedding 字段隐含记录当前使用的 embedding provider + model。切换 model 时维度可能变化，触发存量数据 reindex。

VectorField 内部属性增加 `embeddingModel`（字符串，如 `"openai/text-embedding-3-small"`），用于：
- 启动校验：新配置的 model 维度是否与 `@dimensions` 一致
- 变更检测：model 变更时标记需要 reindex
- 搜索过滤：确保搜索时使用的 query 向量与存量向量来自同一 model

### 全局技术配置（application.properties）

```properties
# 嵌入提供商（必填）
flexmodel.vector.embedding.provider=openai
flexmodel.vector.embedding.model=text-embedding-3-small

# 嵌入模式（sync = 写入时同步嵌入，async = 后台异步补填）
flexmodel.vector.embedding.mode=sync

# 搜索加速引擎（可选，不配则使用内置 InMemory 实现）
flexmodel.vector.search-engine=inmemory
# flexmodel.vector.search-engine=pgvector    # 数据量大时切换
```

配置映射通过新增 `@ConfigMapping(prefix = "flexmodel.vector")` 接口实现，与现有 `FlexmodelConfig`（`flexmodel.datasource`）、`StorageProviderConfig`（`flexmodel.storage`）风格一致。

---

## 关键接口

### EmbeddingProvider（文本 → 向量）

```java
public interface EmbeddingProvider {
    /** 提供商名称，如 "openai"、"ollama" */
    String getName();

    /** 当前使用的模型标识，如 "openai/text-embedding-3-small" */
    String getModelId();

    /** 该提供商生成的向量维度 */
    int getDimensions();

    /** 该提供商推荐的相似度算法，搜索时自动使用 */
    default String similarityMetric() { return "cosine"; }

    /** 单条文本嵌入，失败时抛 VectorException */
    float[] embed(String text) throws VectorException;

    /** 批量文本嵌入，减少 API 调用次数。
     *  实现方内部分批（如 OpenAI 每 2048 条），调用方无需关心 */
    List<float[]> embedBatch(List<String> texts) throws VectorException;

    /** 该提供商的向量是否已归一化（单位向量）。
     *  true → InMemory 引擎直接算余弦相似度
     *  false → InMemory 引擎计算时先归一化 */
    default boolean isNormalized() { return true; }
}
```

### VectorSearchEngine（搜索加速层）

负责向量的索引和搜索。内置 `InMemoryVectorSearchEngine`（< 50K 够用），可替换为 `PgVectorSearchEngine` 或 `MilvusSearchEngine`。

```java
public interface VectorSearchEngine {
    /** 引擎名称，如 "inmemory"、"pgvector"、"milvus" */
    String getName();

    /** 写入/更新一条向量。
     *  metadata 由系统自动填充：source 文本原文 + 记录 ID */
    void upsert(String collection, String id, float[] vector, Map<String, Object> metadata);

    /** 删除一条向量 */
    void delete(String collection, String id);

    /**
     * 向量相似度搜索
     * @param collection    集合名称（格式：{projectId}_{modelName}，多租户隔离）
     * @param queryVector   查询向量（必须与存量向量来自同一 embedding model）
     * @param topK          返回数量
     * @param threshold     相似度阈值（0~1），低于此值的结果过滤掉
     * @param candidateIds  可选的候选 ID 集合（结构化过滤后的子集）；为 null 表示全量搜索
     * @return 搜索命中结果（含 metadata，由 VectorSearchService 回填完整记录）
     */
    List<VectorSearchHit> search(String collection, float[] queryVector,
                                  int topK, float threshold, Set<String> candidateIds);

    /** 删除整个集合（模型被删除或嵌入配置被移除时调用） */
    void deleteCollection(String collection);

    /** 查询集合中向量数量 */
    long count(String collection);
}
```

```java
/** 向量搜索命中结果 */
public record VectorSearchHit(
    String id,
    double score,
    Map<String, Object> metadata   // 系统自动填充：source 文本原文 + 记录 ID
) {}
```

> `search()` 的 `candidateIds` 参数用于"结构化过滤 + 向量排序"混合场景：先执行 filter 得到候选 ID 集，再在此集内做向量搜索。这比在引擎内做元数据过滤更简单，且复用了现有 `DataService.findAll(Query)` 的过滤能力。

### VectorSearchRequest / VectorSearchResult（REST 契约）

```java
/** 向量搜索请求（POST .../records/search 的 body） */
public record VectorSearchRequest(
    String filter,              // 结构化过滤字符串（与现有 filter 格式一致），可为 null
    SimilarityClause similarity, // 向量搜索子句（必填）
    Integer page,               // 页码，默认 1
    Integer size                // 每页数量，默认 10
) {}

/** 向量相似度搜索子句 */
public record SimilarityClause(
    String field,          // 搜索哪个 Vector 字段名
    String query,          // 文本语义搜索（与 similarTo 互斥）
    String similarTo,      // 以记录 ID 找相似记录（与 query 互斥）
    int topK,              // 返回数量，默认 10
    float threshold        // 相似度阈值，默认 0.5
) {}

/** 向量搜索结果 */
public record VectorSearchResult(
    List<Map<String, Object>> list,  // 完整记录 + similarityScore + source
    long total
) {}
```

> **注意**：`SimilarityClause` 是 REST DTO，**不是** `Query` 对象的内部属性。`Query` 类（`dev.flexmodel.query.Query`）是内部查询抽象，REST 层的 `RecordResource.findPagingRecords` 并不直接接收 `Query`，而是接收 filter/sort/expand 原始字符串。向量搜索走独立端点 + 独立 DTO，避免侵入 `Query` 类的现有结构。

### 各数据库字段类型映射

Vector 字段类型的存储格式由数据库决定：

| 数据库 | 列类型 | 搜索方式 |
|--------|-------|---------|
| PostgreSQL + PGVector 扩展 | `VECTOR(1536)` | SQL 层 `<=>` ANN |
| PostgreSQL（无 PGVector） | `TEXT`（JSON 序列化 float 数组） | InMemory 引擎 |
| MySQL | `BLOB` / `JSON` | InMemory 引擎 |
| SQLite | `BLOB` / `TEXT`（JSON） | InMemory 引擎 |
| Oracle | `BLOB` | InMemory 引擎 |
| DB2 | `BLOB` | InMemory 引擎 |
| MongoDB | 原生数组 | `$vectorSearch` |

字段类型映射走现有的 `TypedField` + `SqlTypeHandler` 机制。需新增 `VectorSqlTypeHandler` 处理 `float[]` ↔ 数据库列的序列化/反序列化（默认实现：JSON 序列化为文本/BLOB；PGVector 实现使用原生 VECTOR 类型）。

DDL 列创建在 `SqlSchemaService` / `StandardColumnExporter` 中为 VectorField 增加分支，生成对应列类型。

**向量归一化契约**：`EmbeddingProvider.isNormalized()` 声明向量是否归一化。`InMemoryVectorSearchEngine` 在计算余弦相似度时，对非归一化向量先做归一化。PGVector 的 `<=>` 运算符自行处理归一化。这个契约在接口层面明确，不留给实现层自行决定。

---

## 模块结构

### 引擎层：`flexmodel-core`

```
flexmodel-core/.../model/field/
  └── VectorField.java              # Vector 字段类型，继承 TypedField<float[], VectorField>
                                    #   属性：dimensions (int), source (List<String>),
                                    #         template (String, 可选),
                                    #         embeddingModel (String, 自动填充)
                                    #   重写 equals/hashCode（dimensions 参与比较）

flexmodel-core/.../vector/
  ├── EmbeddingProvider.java        # 接口：文本 → 向量（含 isNormalized 契约）
  ├── VectorSearchEngine.java       # 接口：搜索加速层
  ├── VectorSearchHit.java          # record: {id, score, metadata}
  └── VectorException.java          # 统一异常类
```

> `SimilarityClause`、`VectorSearchRequest`、`VectorSearchResult` 是 REST 层 DTO，放在 `flexmodel-server` 的 `vector/dto/` 包，不放 `flexmodel-core`。`flexmodel-core` 只放与框架无关的接口。

具体实现类在 `flexmodel-server` 层：
- `InMemoryVectorSearchEngine` — 内置轻量实现，Java 层计算余弦相似度
- `PgVectorSearchEngine` — PGVector 插件实现
- `MilvusVectorSearchEngine` 等 — 未来扩展

### 服务层：`flexmodel-server`

```
vector/
  ├── VectorEmbeddingProcessor.java     # EventListener，onPreChange 时检查 @embedding 注解
  │                                      #   source 文本变化检测 → 跳过不变 → 调 embed() 填值
  │                                      #   async 模式下跳过嵌入，留 null 由后台补填
  ├── VectorSyncListener.java           # EventListener，onChanged 时同步到 VectorSearchEngine
  │                                      #   collection 格式：{projectId}_{modelName}
  ├── VectorSearchService.java          # 搜索业务逻辑
  │                                      #   解析 VectorSearchRequest → 获取 queryVector → search → 回填记录
  │                                      #   filter 非空时先做结构化过滤拿候选 ID，再向量搜索
  │                                      #   按 score 降序排列（不走 Query.sort）
  ├── VectorReindexService.java         # 存量数据回填服务
  │                                      #   异步执行，支持进度查询
  │                                      #   embedding model 变更时自动触发
  ├── InMemoryVectorSearchEngine.java   # 内置搜索引擎实现（全量内存索引）
  ├── VectorResource.java               # REST 端点：reindex 触发 + 进度查询
  └── dto/
      ├── VectorSearchRequest.java      # {filter, similarity, page, size}
      ├── VectorSearchResult.java       # {list, total}
      ├── SimilarityClause.java         # {field, query, similarTo, topK, threshold}
      ├── VectorReindexRequest.java     # {modelName, async: true}
      └── VectorReindexStatus.java      # {modelName, status, progress, total, errorMessage}
```

**REST 端点分布**：

| 端点 | 位置 | 说明 |
|------|------|------|
| `POST .../records/search` | `RecordResource`（新增方法） | 向量搜索，body 为 `VectorSearchRequest`。放在 RecordResource 因为它是记录查询的变体，保持 records 路径一致 |
| `POST .../models/{modelName}/reindex` | `VectorResource`（新建） | 触发存量数据回填 |
| `GET .../models/{modelName}/reindex/status` | `VectorResource`（新建） | 查询回填进度 |

@embedding 配置不需要独立 REST API——Vector 字段的 dimensions/source/template 属性通过已有 `PUT /projects/{id}/models/{modelName}/fields/{fieldName}` 管理（接收 `TypedField` body，新增的 `VectorField` 子类自动支持），和修改字段的 `@unique`、`@default` 一样。

### 前端：flexmodel-ui

前端在建模页字段列表中增加 Vector 类型选项和 @embedding 配置。

```
pages/DataModeling/components/
  ├── EntityView.tsx              # 修改：字段类型下拉增加 Vector 选项
  ├── EmbeddingConfig.tsx         # 新增：向量字段的 @embedding + @dimensions 配置面板

services/
  └── vector.ts                   # 新增：向量搜索 API 调用（POST .../records/search）
```

---

## 实现步骤

### Step 1: 定义引擎层接口

- 在 `flexmodel-core/.../vector/` 中创建 `EmbeddingProvider`、`VectorSearchEngine`、`VectorSearchHit`、`VectorException`
- 只定义接口和 record，不做实现
- `EmbeddingProvider` 包含 `isNormalized()` 契约和 `getModelId()` 版本追踪

### Step 2: 新增 Vector 字段类型

- 在 `ScalarType` 枚举新增 `VECTOR("Vector")` 值及 `VECTOR_TYPE = "Vector"` 常量
- 创建 `VectorField.java`，继承 `TypedField<float[], VectorField>`，构造时传入 `"Vector"` 类型名
  - 属性：`dimensions (int)`, `source (List<String>)`, `template (String)`, `embeddingModel (String)`
  - 重写 `equals/hashCode`（dimensions 参与比较）
- 在 `TypedFieldMixIn` 的 `@JsonSubTypes` 新增 `@JsonSubTypes.Type(value = VectorField.class, name = ScalarType.VECTOR_TYPE)`
- 在 `FlexmodelCoreModule` 构造函数新增 `setMixInAnnotation(VectorField.class, TypedFieldMixIn.class)`
- 新增 `VectorSqlTypeHandler`，在 `SqlContext` 注册（处理 `float[]` 序列化）

### Step 3: ASTNodeConverter 支持 Vector 类型 + @dimensions/@embedding 注解

- `toSchemaField()`：在 switch 中**新增 Vector case**（关键：否则 Vector 会落入 default 分支被误判为 Relation/Enum）
  - `case ScalarType.VECTOR_TYPE ->` 创建 `VectorField`，解析 `@dimensions` 注解提取维度，解析 `@embedding` 注解提取 source 列表和 template
- `fromSchemaField()`：新增 `case VectorField vectorField ->` 反向输出 FML
  - 输出 `embedding : Vector @dimensions(1536) @embedding(source: ["title", "content"], template: "{title}: {content}")`
- FML round-trip 测试覆盖

> **不需要改 JavaCC grammar**：`Vector` 是普通 Identifier，`@dimensions` / `@embedding` 是普通注解，语法层已支持。列表参数 `source: [...]` 复用 `@index(fields: [...])` 的列表解析机制。

### Step 4: 实现 VectorEmbeddingProcessor（EventListener）

- 实现 `EventListener`，覆盖 `onPreChange(PreChangeEvent)`
- `supports()` 仅对 PRE_INSERT / PRE_UPDATE 返回 true
- 检查目标模型的字段中是否有 `VectorField` 且带 @embedding 配置（source 非空）
- source 文本变化检测：比对 `event.getOldData()` 和 `event.getNewData()` 中 source 字段值
  - **实现时验证**：`EventAwareDataService` 的 update 路径是否实际填充了 `oldData`。若未填充或为 null，回退为"每次 update 都重新 embed"（正确性优先，性能其次）
- 同步模式：source 变化 → `embed()` → 通过 `event.setNewData()` 回写 Vector 
- 异步模式：跳过嵌入，Vector 字段留 null
- 注入 `EmbeddingProvider`（CDI）
- 在 `EngineConfig` 的 `sessionFactory` producer 方法中注册（增加参数 + `addListener()`，与现有 `AuditDataEventListener` 等一致）

### Step 5: 实现 VectorSyncListener（同步到搜索层）

- 实现 `EventListener`，覆盖 `onChanged(ChangedEvent)`
- `supports()` 仅对 INSERTED / UPDATED / DELETED 返回 true
- INSERTED/UPDATED → `VectorSearchEngine.upsert(collection, id, vector, metadata)`
- DELETED → `VectorSearchEngine.delete(collection, id)`
- collection 格式：`{projectId}_{modelName}`
- metadata 由系统自动填充（source 文本 + 记录 ID）
- `VectorSearchEngine` 未配置/无向量字段 → 跳过
- **事务边界说明**：`onChanged` 在写入成功后触发。若引擎 upsert 失败，DB 数据已落库但索引缺失——这是可接受的最终一致状态，因为索引可随时通过 reindex 重建。失败记录日志，不阻断主流程。`getOrder()` 返回较大值（如 1000，与 `RealtimeEventListener` 一致），确保在核心监听器之后执行
- 在 `EngineConfig` producer 方法中注册

### Step 6: 实现 InMemoryVectorSearchEngine（内置默认实现）

- Java 层余弦相似度计算
- 非 `isNormalized()` 向量先归一化再计算
- **全量内存索引**（非 Caffeine 缓存）：相似度搜索需要访问全部向量，带驱逐的缓存会丢失必需数据。使用 `ConcurrentHashMap<String, float[]>` 持有全量向量
- 容量限制：~50K 条 / 1536 维 ≈ ~300MB 内存
- 冷启动策略：服务启动后惰性加载（首次搜索时从 DB 全量读取 Vector 列）
- `search()` 的 `candidateIds` 非空时，仅在该子集内计算相似度

### Step 7: 实现 VectorSearchService + 搜索端点

- 在 `RecordResource` 新增 `POST .../records/search` 方法，接收 `VectorSearchRequest` body
- `VectorSearchService.search(projectId, modelName, request)` 执行流程：
  1. 解析 `similarity` 子句
  2. `query` 模式：`EmbeddingProvider.embed(query)` → queryVector
  3. `similarTo` 模式：优先从 VectorSearchEngine 查向量；引擎未命中则 `DataService.findById` 取记录的 Vector 字段值
  4. `filter` 非空：`DataService.findAll(Query)` with filter → 候选 ID 集合
  5. `VectorSearchEngine.search(collection, queryVector, topK, threshold, candidateIds)` → 命中 ID + score
  6. 批量 `DataService.findById` 回填完整记录（一次 batch）
  7. 返回结果附加 `similarityScore` 字段 + source 字段原文，按 score 降序排列

### Step 8: 实现 VectorReindexService + VectorResource

- `VectorReindexService`：异步执行 reindex（Quarkus `@Async` 或定时任务）
  - 分批 embed（`EmbeddingProvider.embedBatch` 内部控制批次大小）
  - 支持进度查询
  - embedding model 配置变更时自动标记需要 reindex
  - 部分失败策略：记录失败的 ID，不阻塞整体进度，可重试
- `VectorResource`：
  - `POST /projects/{projectId}/models/{modelName}/reindex` — 触发存量回填
  - `GET /projects/{projectId}/models/{modelName}/reindex/status` — 查询进度

### Step 9: 搜索引擎切换流程（文档层面描述）

- InMemory → PGVector 切换流程：
  1. 配置变更 `flexmodel.vector.search-engine=pgvector`
  2. 调 `POST .../reindex` 全量重建到 PGVector
  3. InMemory 索引清空
- 迁移端点延迟到后续版本——InMemory 数据量小（<50K），reindex 成本可控，初期不需要复杂迁移工具

### Step 10: 前端

- 字段类型下拉增加 Vector 选项
- 向量字段选中后显示 @dimensions + @embedding 配置面板（source 多选、template 可选）
- 向量搜索通过 `POST .../records/search` 接口

### Step 11: 更新 feature_list.json 和 progress.md

---

## 验证

1. `mvn compile -pl '!flexmodel-engine/flexmodel-maven-plugin'` → BUILD SUCCESS
2. `mvn test -pl flexmodel-engine` → all passed
3. FML round-trip：`embedding : Vector @dimensions(1536) @embedding(source: ["title", "content"], template: "{title}: {content}")` → 解析 → 输出回 FML 不丢失
4. 向量搜索：`POST .../records/search` body 含 `similarity` 子句 → 返回带 similarityScore 的完整记录 + source 原文
5. filter + similarity 混合：filter 过滤后的候选集内做向量搜索
6. source 不变时跳过 embed：更新非 source 字段，Vector 字段值不变（需 oldData 可用时）
7. 前端：字段类型下拉出现 "Vector" 选项，选中后显示 @dimensions + @embedding 配置面板

---

## 关键文件

### 后端 — 修改

| 文件 | 改动 |
|---|---|
| `ScalarType.java` (engine-core/model/field) | 新增 `VECTOR("Vector")` 枚举值 + `VECTOR_TYPE` 常量 |
| `ASTNodeConverter.java` (engine-core/parser) | `toSchemaField()` 新增 `VECTOR_TYPE` case（**避免落入 default 被误判为 Relation**）；`fromSchemaField()` 新增 `VectorField` case；解析 `@dimensions` / `@embedding` 注解 |
| `EngineConfig.java` (server/common/config) | `sessionFactory` producer 方法新增 `VectorEmbeddingProcessor` + `VectorSyncListener` 参数及 `addListener()` 调用 |
| `RecordResource.java` (server/data) | 新增 `POST .../records/search` 方法，接收 `VectorSearchRequest` |
| `TypedFieldMixIn.java` (engine-core/supports/jackson) | `@JsonSubTypes` 新增 `VectorField → "Vector"` |
| `FlexmodelCoreModule.java` (engine-core/supports/jackson) | 新增 `setMixInAnnotation(VectorField.class, TypedFieldMixIn.class)` |
| `SqlContext.java` (engine-core/sql) | 注册 `VectorSqlTypeHandler` |
| `SqlSchemaService.java` / `StandardColumnExporter.java` (engine-core/sql) | VectorField DDL 列创建分支 |

### 后端 — 新建

| 文件 | 作用 |
|---|---|
| `VectorField.java` (engine-core/model/field) | Vector 字段类型，含 dimensions/source/template/embeddingModel |
| `EmbeddingProvider.java` (engine-core/vector) | 嵌入接口（含 isNormalized + getModelId） |
| `VectorSearchEngine.java` (engine-core/vector) | 搜索加速层接口 |
| `VectorSearchHit.java` (engine-core/vector) | 搜索结果 record（含 metadata） |
| `VectorException.java` (engine-core/vector) | 异常类 |
| `VectorSqlTypeHandler.java` (engine-core/sql/type) | Vector 字段 SQL 序列化/反序列化（float[] ↔ 列） |
| `VectorEmbeddingProcessor.java` (server/vector) | @embedding 值自动生成处理器（含 source 变化检测） |
| `VectorSyncListener.java` (server/vector) | 同步到搜索加速层 |
| `VectorSearchService.java` (server/vector) | 搜索业务逻辑 |
| `VectorReindexService.java` (server/vector) | 存量回填服务（异步 + 进度） |
| `InMemoryVectorSearchEngine.java` (server/vector) | 内置搜索引擎实现（全量内存索引） |
| `VectorResource.java` (server/vector) | reindex 触发 + 进度查询端点 |
| `VectorSearchRequest.java` / `VectorSearchResult.java` / `SimilarityClause.java` (server/vector/dto) | 搜索 REST DTO |
| `VectorReindexRequest.java` / `VectorReindexStatus.java` (server/vector/dto) | reindex DTO |

### 前端 — 修改

| 文件 | 改动 |
|---|---|
| `EntityView.tsx` / `FieldList.tsx` (pages/DataModeling/components) | 字段类型下拉增加 "Vector" 选项 |

### 前端 — 新建

| 文件 | 作用 |
|---|---|
| `EmbeddingConfig.tsx` (pages/DataModeling/components) | @dimensions + @embedding 配置面板 |
| `vector.ts` (services) | 向量搜索 API（POST .../records/search） |

### 不改

| 文件 | 说明 |
|---|---|
| `ModelParser.jj` (engine-core/javacc) | `Vector` 是普通 Identifier，`@dimensions`/`@embedding` 是普通注解，语法不需要改 |
| `EventListener.java` (engine-core/event) | `VectorEmbeddingProcessor` 和 `VectorSyncListener` 实现此接口（已有 `onPreChange`/`onChanged`/`supports`/`getOrder`） |
| `PreChangeEvent.java` / `ChangedEvent.java` (engine-core/event) | `PreChangeEvent` 已有 `setNewData()`/`getOldData()`，无需改 |
| `Query.java` (engine-core/query) | 向量搜索走独立端点 + DTO，不侵入 Query 类 |
| `EntityDefinition.java` (engine-core/model) | Vector 是字段类型，不是模型级属性 |

---

## 与原方案的关键差异（落地修正记录）

| # | 原方案 | 问题 | 修正 |
|---|--------|------|------|
| 1 | `Vector(1536)` 参数化类型语法 | `ModelParser.jj` 的 `Type()` 只支持 `Identifier` + `[]`，不支持 `Type(args)`；`ScalarType.fromType("Vector(1536)")` 返回 null；`ASTNodeConverter` switch 会将 `Vector(1536)` 落入 default 误判为 Relation | 改为 `Vector @dimensions(1536)` 注解，与 `String @length`、`Float @precision` 一致 |
| 2 | `POST .../records` body 为 Query 含 similarity | 现有 `POST .../records` 是创建记录（createRecord）；`GET .../records` 是分页查询走 query string，不接受 body；`findPagingRecords` 接收原始参数而非 Query 对象 | 新增 `POST .../records/search` 专用搜索端点 |
| 3 | `select: [...]` / `orderBy: "similarity.score DESC"` | `Query` 实际字段是 `projection`（非 `select`）；`OrderBy.Sort.field` 是 `QueryField`，不支持按计算字段排序 | 相似度排序在 `VectorSearchService` 内部完成，不走 `Query.sort` |
| 4 | `SimilarityClause` 作为 `Query` 的属性 | `Query` 是内部抽象，REST 层不直接暴露 | `SimilarityClause` 作为 REST DTO 放 server 层 |
| 5 | InMemory 引擎用 Caffeine 缓存 | 相似度搜索需全量向量，带驱逐的缓存会丢失必需数据 | 改为全量内存索引（`ConcurrentHashMap`） |
| 6 | `VectorSearchEngine.search()` 的 filter 参数（Map） | 结构化过滤应复用现有 `DataService.findAll(Query)` 能力 | 改为 `candidateIds`（Set），由 service 层先过滤再传入 |
| 7 | similarTo 无引擎时直接从记录计算相似度 | 无引擎需全表扫描向量列，与 InMemory 同等成本，并非轻量兜底 | similarTo 优先走引擎；引擎未命中时 `findById` 取目标向量，但仍需引擎持有其他向量做比对——故 InMemory 为默认且始终启用 |
| 8 | reindex 端点位置未明确 | 不应塞入 RecordResource | 放独立 `VectorResource` |
