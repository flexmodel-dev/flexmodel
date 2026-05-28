# FML Seed 语法实现计划

## 目标

实现 `.fml` 文件格式，支持在同一个文件中同时定义模型（model/enum）和种子数据（seed），替代当前 `.idl` + `.json` 分离的数据导入方式。`.idl` 保持向后兼容。

## 语法示例

```fml
// 模型定义（与 IDL 完全兼容）
model User {
  id: String @id @default(uuid()),
  name: String @length(255),
  role: UserRole,
}

enum UserRole {
  ADMIN, EDITOR, VIEWER
}

// 种子数据（新增）
seed UserRole {
  { code: "ADMIN", label: "管理员" },
  { code: "EDITOR", label: "编辑" },
  { code: "VIEWER", label: "访客" },
}

seed User @strategy("skip_exists") @match("email") {
  { name: "管理员", email: "admin@system.local", role: "ADMIN" },
}
```

seed 块中的值类型支持：字符串、整数、布尔、null、数组 `[...]`、嵌套对象 `{...}`、函数调用 `uuid()`。`@strategy` 和 `@match` 为可选注解，本阶段先解析但不做运行时策略处理（Phase 1 仅做 insert）。

## 影响范围

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `flexmodel-core/src/main/javacc/ModelParser.jj` | 修改 | 新增 seed 语法产生式 |
| `flexmodel-core/src/main/java/dev/flexmodel/parser/ASTNodeConverter.java` | 修改 | 新增 SeedDeclaration 到 ImportData 的转换 |
| `flexmodel-core/src/main/java/dev/flexmodel/session/SessionFactory.java` | 修改 | 新增 `loadFMLString()`，更新 `loadScript()` |
| `flexmodel-codegen/src/main/java/dev/flexmodel/codegen/Configuration.java` | 修改 | 支持 `.fml` 文件解析 |
| `flexmodel-core/src/test/` | 新增 | 测试用例和 `.fml` 样本文件 |
| `docs/fml-design.md` | 新增 | FML 语言设计文档 |

## 实施步骤

### Task 1: 扩展 JavaCC 语法 (`ModelParser.jj`)

文件: `flexmodel-engine/flexmodel-core/src/main/javacc/ModelParser.jj`

**1.1 新增关键字**

在 TOKEN 区域新增 `seed` 和 `null` 关键字：

```
TOKEN : { < SEED: "seed" > }
TOKEN : { < NULL: "null" > }
TOKEN : { < HASH: "#" > }
```

**1.2 新增 AST 节点类**

在 `PARSER_BEGIN` 区块中新增：

```java
// 种子数据声明
public static class SeedDeclaration implements ASTNode {
    public String modelName;
    public List<Map<String, Object>> records = new ArrayList<>();
    public List<Annotation> annotations = new ArrayList<>();
    public SeedDeclaration(String modelName) { this.modelName = modelName; }
    public void addRecord(Map<String, Object> record) { records.add(record); }
    public void addAnnotation(Annotation ann) { annotations.add(ann); }
    // toString() 方法用于 IDL 反向序列化
}
```

**1.3 扩展 CompilationUnit 入口产生式**

```
List CompilationUnit() : { ... }
{
    (
        node = ModelDeclaration()
      | node = EnumDeclaration()
      | node = SeedDeclaration()      // 新增
    )* <EOF>
    { return astList; }
}
```

**1.4 新增 SeedDeclaration 产生式**

```
SeedDeclaration SeedDeclaration() : {
    String modelName;
    SeedDeclaration seed;
    Annotation ann;
}
{
    <SEED> modelName = Identifier()
    { seed = new SeedDeclaration(modelName); }
    ( ann = SeedAnnotation() { seed.addAnnotation(ann); } )*
    <LBRACE>
    ( SeedRecordList(seed) )?
    <RBRACE>
    { return seed; }
}
```

**1.5 新增 SeedRecord 和 SeedValue 产生式**

需要支持的值类型：
- 字符串字面量 `"hello"`
- 整数字面量 `123`
- 布尔字面量 `true`/`false`
- `null`
- 数组 `[1, 2, 3]`
- 嵌套对象 `{ key: value }`
- 函数调用 `uuid()`

关键产生式：
- `SeedRecordList` - 记录列表，每条记录用逗号分隔
- `SeedRecord` - 单条记录 `{ key: value, ... }`
- `SeedValue` - 值表达式（字符串/数字/布尔/null/数组/对象/函数调用）
- `SeedObjectValue` - 嵌套对象值
- `SeedArrayValue` - 数组值

### Task 2: 更新 ASTNodeConverter

文件: `flexmodel-engine/flexmodel-core/src/main/java/dev/flexmodel/parser/ASTNodeConverter.java`

**2.1 扩展 `toSchemaObject` 方法**

由于 `SeedDeclaration` 不是 `SchemaObject`，需新增一个独立转换方法：

```java
public static ModelImportBundle.ImportData toImportData(ModelParser.SeedDeclaration seed) {
    ModelImportBundle.ImportData importData = new ModelImportBundle.ImportData();
    importData.setModelName(seed.modelName);
    importData.setValues(seed.records);
    return importData;
}
```

**2.2 新增工具方法 `parseFML`**

解析 FML 字符串，返回模型列表和种子数据列表：

```java
public static FMLParseResult parseFML(String fmlString) throws ParseException {
    ModelParser parser = new ModelParser(new StringReader(fmlString));
    List<ModelParser.ASTNode> ast = parser.CompilationUnit();
    
    List<SchemaObject> models = new ArrayList<>();
    List<ModelImportBundle.ImportData> seeds = new ArrayList<>();
    
    for (ModelParser.ASTNode node : ast) {
        if (node instanceof ModelParser.SeedDeclaration seed) {
            seeds.add(toImportData(seed));
        } else {
            SchemaObject obj = toSchemaObject(node);
            if (obj != null) models.add(obj);
        }
    }
    return new FMLParseResult(models, seeds);
}
```

新增 `FMLParseResult` record 类存放解析结果。

### Task 3: 更新 SessionFactory

文件: `flexmodel-engine/flexmodel-core/src/main/java/dev/flexmodel/session/SessionFactory.java`

**3.1 新增 `loadFMLString()` 方法**

```java
public void loadFMLString(String schemaName, String fmlString) {
    // 调用 ASTNodeConverter.parseFML()
    // processModels() 处理模型
    // processImportData() 处理种子数据
}
```

**3.2 更新 `loadScript()` 方法**

在文件扩展名分发中新增 `.fml` 分支：

```java
if (scriptName.endsWith(".fml")) {
    loadFMLString(schemaName, scriptString);
} else if (scriptName.endsWith(".json")) {
    loadJSONString(schemaName, scriptString);
} else if (scriptName.endsWith(".idl")) {
    loadIDLString(schemaName, scriptString);
}
```

**3.3 更新 `loadIDLString()` 的兼容性**

`loadIDLString()` 保持不变，仅处理 model/enum（不含 seed），确保向后兼容。

### Task 4: 更新 Codegen Configuration

文件: `flexmodel-engine/flexmodel-codegen/src/main/java/dev/flexmodel/codegen/Configuration.java`

**4.1 在 `getImportDescribes()` 中新增 `.fml` 分支**

```java
} else if (importScript.endsWith(".fml")) {
    String content = Files.readString(scriptFile.toPath());
    ASTNodeConverter.FMLParseResult result = ASTNodeConverter.parseFML(content);
    models.addAll(result.models());
    data.addAll(result.seeds());
}
```

**4.2 更新 SchemaConfig 默认值**

`SchemaConfig.importScript` 的默认值保持 `"import.json"` 不变（避免破坏现有项目），但文档中推荐使用 `.fml`。

### Task 5: 编写测试

**5.1 新增测试 FML 文件**

文件: `flexmodel-engine/flexmodel-core/src/test/resources/sample_input.fml`

包含 model + enum + seed 的完整示例。

**5.2 新增解析器测试**

文件: `flexmodel-engine/flexmodel-core/src/test/java/dev/flexmodel/FMLParserTest.java`

测试用例：
- 解析包含 model + seed 的 FML 字符串
- 验证 seed 记录中的各种值类型（字符串、数字、布尔、null、数组、嵌套对象）
- 验证 seed 注解（@strategy、@match）解析正确
- 验证 model + enum + seed 混合解析
- 验证空 seed 块
- 验证 ASTNodeConverter 转换结果

**5.3 新增 SessionFactory 集成测试**

验证 `loadFMLString()` 能正确加载模型和种子数据。

### Task 6: 生成设计文档

文件: `docs/fml-design.md`

记录 FML 语言规范、语法示例、与 IDL/JSON 的对比、迁移指南。

## 验证方式

```bash
# 编译验证（含 JavaCC 生成）
cd flexmodel-engine && mvn clean compile -pl flexmodel-core

# 运行测试
cd flexmodel-engine && mvn test -pl flexmodel-core -Dtest="FMLParserTest,IDLParserTest"

# 全模块编译验证
mvn clean compile -pl '!flexmodel-engine/flexmodel-maven-plugin'
```
