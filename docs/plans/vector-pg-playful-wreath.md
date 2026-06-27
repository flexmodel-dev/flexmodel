# feat-011: Vector — 向量字段类型与语义搜索

## Context

Flexmodel 定位是"统一数据访问层"，数据 CRUD 通过 `EventAwareDataService` 执行。新增向量能力后，数据从"能存能查"升级到"能懂能联想"。

**核心设计决策**：向量是一个**字段类型**，和 `String`、`Int` 没有本质区别。它存于用户表中，走 DataService 管理生命周期。独立的搜索加速层（VectorSearchEngine）只在数据量大时才需要。

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
├──────────────────────────────────────────────────────┤
│ Layer 3: 搜索加速层（可选，数据量大时启用）              │
│   VectorSearchEngine 接口（upsert / delete / search）  │
│   内置 InMemory 实现（< 50K 够用）                     │
│   可替换为 PGVector / Milvus                           │
└──────────────────────────────────────────────────────┘
```

### 写入线

```
CRUD → DataService
  ↓ beforeInsert / beforeUpdate
  ↓ 检查字段是否有 @embedding 注解
  ↓ 有 → EmbeddingProvider.embed(拼接文本) → 填 Vector 字段
  ↓
  ↓ afterCommit
  ↓ 如果有 VectorSearchEngine 配置
  ↓ → VectorSearchEngine.upsert(id, vector, metadata)
```

### 搜索线

```
POST /projects/{id}/search → VectorSearchService
  ↓ query 模式：EmbeddingProvider.embed(query) → 查询向量
  ↓ similarTo 模式：从 VectorSearchEngine 查该 ID 的向量
  ↓ VectorSearchEngine.search(collection, queryVector, topK, threshold, filter)
  ↓ DataService.findById 回填完整记录（可选）
  → 返回 [{id, score, record}]
```

---

## 配置方式

### FML：向量字段 + @embedding 注解

向量在 FML 中就是一个字段类型，`@embedding` 注解声明该字段的值由嵌入提供商自动生成：

```fml
model Article {
  title : String,
  content : String,

  # 简单用法：source 字段空格拼接 → embed → 自动填入此字段
  embedding : Vector(1536) @embedding(source: ["title", "content"])
}
```

```fml
model Article {
  title : String,
  content : String,

  # 精确用法：模板格式化，影响搜索质量
  embedding : Vector(1536) @embedding(source: ["title", "content"], template: "{title}: {content}")
}
```

```fml
model Article {
  title : String,
  content : String,
  summary : String,

  # 同一模型支持多个向量字段，各自独立
  title_embedding : Vector(256) @embedding(source: ["title"]),
  fulltext_embedding : Vector(1536) @embedding(source: ["title", "content"], template: "{title}\n{content}")
}
```

`source` — 必填，声明哪些字段的值参与嵌入。

`template` — 可选，控制文本拼接格式。不写则 source 字段值用空格拼接。

`Vector(N)` — N 为向量维度，由 EmbeddingProvider.getDimensions() 校验，不匹配则启动时报错。

FML 解析器已支持任意注解名和参数，不需要改 JavaCC grammar。

### 全局技术配置（application.properties）

```properties
# 嵌入提供商（必填）
flexmodel.vector.embedding.provider=openai
flexmodel.vector.embedding.model=text-embedding-3-small

# 搜索加速引擎（可选，不配则使用轻量 InMemory 实现）
flexmodel.vector.search-engine=inmemory
# flexmodel.vector.search-engine=pgvector    # 数据量大时切换
```

---

## 关键接口

### EmbeddingProvider（文本 → 向量）

```java
public interface EmbeddingProvider {
    /** 提供商名称，如 "openai"、"ollama" */
    String getName();

    /** 该提供商生成的向量维度 */
    int getDimensions();

    /** 该提供商推荐的相似度算法，搜索时自动使用 */
    default String similarityMetric() { return "cosine"; }

    /** 单条文本嵌入，失败时抛 VectorException */
    float[] embed(String text) throws VectorException;

    /** 批量文本嵌入，减少 API 调用次数 */
    List<float[]> embedBatch(List<String> texts) throws VectorException;
}
```

### VectorSearchEngine（搜索加速层）

负责向量的索引和搜索。内置 `InMemoryVectorSearchEngine`（< 50K 够用），可替换为 `PgVectorSearchEngine` 或 `MilvusSearchEngine`。

```java
public interface VectorSearchEngine {
    /** 引擎名称，如 "inmemory"、"pgvector"、"milvus" */
    String getName();

    /** 写入/更新一条向量 */
    void upsert(String collection, String id, float[] vector, Map<String, Object> metadata);

    /** 删除一条向量 */
    void delete(String collection, String id);

    /**
     * 向量相似度搜索
     * @param collection   集合名称（模型名）
     * @param queryVector  查询向量
     * @param topK         返回数量
     * @param threshold    相似度阈值（0~1），低于此值的结果过滤掉
     * @param filter       可选的元数据过滤条件（如 {"status": "open"}），
     *                     语义与 flexmodel 现有查询条件一致；为 null 则不过滤
     */
    List<VectorSearchHit> search(String collection, float[] queryVector,
                                  int topK, float threshold, Map<String, Object> filter);

    /** 删除整个集合（模型被删除或嵌入配置被移除时调用） */
    void deleteCollection(String collection);

    /** 查询集合中向量数量 */
    long count(String collection);
}
```

```java
/** 向量搜索命中结果 */
public record VectorSearchHit(String id, double score, Map<String, Object> metadata) {}
```

### 各数据库字段类型映射

Vector 字段类型的存储格式由数据库决定：

| 数据库 | 列类型 | 搜索方式 |
|--------|-------|---------|
| PostgreSQL + PGVector 扩展 | `VECTOR(1536)` | SQL 层 `<=>` ANN |
| PostgreSQL（无 PGVector） | `REAL[]` | Java 计算 |
| MySQL | `BLOB` | InMemory 引擎 |
| SQLite | `BLOB` | InMemory 引擎 |
| Oracle | `BLOB` | InMemory 引擎 |
| DB2 | `BLOB` | InMemory 引擎 |
| MongoDB | 原生数组 | `$vectorSearch` |

字段类型映射走现有的 `TypedField` + `ValueConverter` 机制，和数据类型的 Boolean→TINYINT 模式一致。

---

## 模块结构

### 引擎层：`flexmodel-core`

```
flexmodel-core/.../model/field/
  └── VectorField.java              # Vector 字段类型，继承 TypedField<float[], VectorField>
                                    #   属性：source (List<String>), template (String), dimensions (int)

flexmodel-core/.../vector/
  ├── EmbeddingProvider.java        # 接口：文本 → 向量
  ├── VectorSearchEngine.java       # 接口：搜索加速层（upsert, delete, search, deleteCollection, count）
  ├── VectorSearchHit.java          # record: {id, score, metadata}
  └── VectorException.java          # 统一异常类
```

具体实现类在 `flexmodel-server` 层：
- `InMemoryVectorSearchEngine` — 内置轻量实现，Java 层计算余弦相似度
- `PgVectorSearchEngine` — PGVector 插件实现
- `MilvusSearchEngine` 等 — 未来扩展

### 服务层：`flexmodel-server`

```
vector/
  ├── VectorSyncListener.java           # EventListener，afterCommit 同步到 VectorSearchEngine
  ├── VectorSearchService.java          # 搜索业务逻辑
  ├── SearchResource.java               # REST: POST /projects/{id}/search（向量搜索 + 相似推荐合并为一个端点）
  ├── VectorEmbeddingProcessor.java     # @embedding 注解处理器（DataService hook，beforeInsert/Update 时调 EmbeddingProvider 填值）
  ├── dto/
  │   ├── VectorSearchRequest.java      # {modelName, query?, similarTo?, topK, threshold?, filter?}
  │   └── VectorSearchResponse.java     # [{id, score, record}]
```

`query` 和 `similarTo` 互斥：传 `query` 是文本搜索，传 `similarTo`（记录 ID）是以记录找记录。

@embedding 配置不需要独立 REST API——Vector 字段的 source/template 属性通过已有 `PUT /projects/{id}/models/{modelName}/fields/{fieldName}` 管理，和修改字段的 `@unique`、`@default` 一样。

### 前端：flexmodel-ui

前端在建模页字段列表中增加 Vector 类型选项和 @embedding 配置。

```
pages/DataModeling/components/
  ├── EntityView.tsx              # 修改：字段类型下拉增加 Vector 选项
  ├── EmbeddingConfig.tsx         # 新增：向量字段的 @embedding 配置面板

services/
  └── vector.ts                   # 新增：向量搜索 API 调用
```

---

## 实现步骤

### Step 1: 定义 EmbeddingProvider 接口

- 在 `flexmodel-core/.../vector/` 中创建接口，只定义不做实现

### Step 2: 定义 VectorSearchEngine 接口 + VectorSearchHit + VectorException

- 在 `flexmodel-core/.../vector/` 中创建接口和 record

### Step 3: 新增 Vector 字段类型

- 注册 `Vector` 字段类型到 flexmodel 的类型系统
- 实现 `ValueConverter<Vector>`（各数据库的序列化/反序列化）
- 与现有 `TypedField` 机制对齐

### Step 4: 新增 VectorField + @embedding 注解解析

- 在 `flexmodel-core/.../model/field/` 中创建 `VectorField.java`，继承 `TypedField<float[], VectorField>`
- 属性：dimensions (int), source (List\<String\>), template (String, 可选)
- ASTNodeConverter 支持字段类型 `Vector(N)` 和注解 `@embedding(source, template)` 解析
- fromSchemaEntity 反向输出 `Vector(1536) @embedding(...)` FML

### Step 5: 实现 VectorEmbeddingProcessor（DataService hook）

- DataService beforeInsert / beforeUpdate 时检查字段是否有 @embedding 注解
- 有注解 → 按 source 和 template 拼接文本 → 调 EmbeddingProvider.embed() → 填入 Vector 字段
- 注入 EmbeddingProvider（CDI）

### Step 6: 实现 VectorSyncListener（异步同步到搜索层）

- 实现 EventListener，afterCommit 时同步向量到 VectorSearchEngine
- 注入 VectorSearchEngine（CDI），未配置则跳过
- 在 EngineConfig 注册

### Step 7: 实现 InMemoryVectorSearchEngine（内置默认实现）

- Java 层余弦相似度计算
- 向量缓存在内存（Caffeine），首次从 DB 加载
- 不支持 upsert/delete 的就地更新（小数据量场景直接重建缓存）

### Step 8: 实现 SearchResource（搜索 + 相似推荐合并为一个端点）

- `POST /projects/{projectId}/search` — `{modelName, query?, similarTo?, topK, threshold?, filter?}`
  - 传 `query` → 文本语义搜索
  - 传 `similarTo` → 以记录找记录（去重、推荐、分类、异常检测）
- `POST /projects/{projectId}/models/{modelName}/reindex` — 存量数据回填（复用现有 model 子资源路径模式）

### Step 9: 前端

- 字段类型下拉增加 Vector 选项
- 向量字段选中后显示 @embedding 配置面板（source 多选、template 可选）
- 向量搜索 API 调用

### Step 10: 更新 feature_list.json 和 progress.md

---

## 验证

1. `mvn compile -pl flexmodel-server` → BUILD SUCCESS
2. `mvn test -pl flexmodel-engine` → all passed
3. FML round-trip：`Vector(1536)` 字段 + `@embedding(source: ["title"], template: "{title}")` → 解析 → 输出回 FML 不丢失
4. 前端：字段类型下拉出现 "Vector" 选项，选中后显示 @embedding 配置面板

---

## 关键文件

### 后端 — 修改

| 文件 | 改动 |
|---|---|
| `ASTNodeConverter.java` (engine-core/parser) | 支持字段上 @embedding 注解解析和反向输出 |
| `EngineConfig.java` (server/common/config) | 注册 VectorSyncListener |
| DataService / EventAwareDataService | beforeInsert/Update hook 调 VectorEmbeddingProcessor |

### 后端 — 新建

| 文件 | 作用 |
|---|---|
| `VectorField.java` (engine-core/model/field) | Vector 字段类型，含 source/template/dimensions 属性 |
| `EmbeddingProvider.java` (engine-core/vector) | 嵌入接口 |
| `VectorSearchEngine.java` (engine-core/vector) | 搜索加速层接口 |
| `VectorSearchHit.java` (engine-core/vector) | 搜索结果 record |
| `VectorException.java` (engine-core/vector) | 异常类 |
| `VectorEmbeddingProcessor.java` (server/vector) | @embedding 值自动生成处理器 |
| `VectorSyncListener.java` (server/vector) | 同步到搜索加速层 |
| `VectorSearchService.java` (server/vector) | 搜索业务逻辑 |
| `SearchResource.java` (server/vector) | 搜索 REST 端点（search + similarTo 合并） |
| `InMemoryVectorSearchEngine.java` (server/vector) | 内置搜索引擎实现 |

### 前端 — 修改

| 文件 | 改动 |
|---|---|
| `EntityView.tsx` (pages/DataModeling/components) | 字段类型下拉增加 "Vector" 选项 |

### 前端 — 新建

| 文件 | 作用 |
|---|---|
| `EmbeddingConfig.tsx` (pages/DataModeling/components) | @embedding 配置面板 |
| `vector.ts` (services) | 向量搜索 API |

### 不改

| 文件 | 说明 |
|---|---|
| `EventListener.java` (engine-core/event) | VectorSyncListener 实现此接口 |
| `ChangedEvent.java` (engine-core/event) | 提取 newData/id |
| `ModelParser.jj` (engine-core/javacc) | FML 语法不需要改 |
| `EntityDefinition.java` (engine-core/model) | Vector 是字段类型，不是模型级属性 |
