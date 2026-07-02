---
sidebar_position: 20
---

# flexmodel-codegen

Flexmodel的代码生成器模块，使用groovy脚本作为模板进行代码生成

1. 引入maven依赖

```xml
<dependency>
    <groupId>dev.flexmodel</groupId>
    <artifactId>flexmodel-codegen</artifactId>
    <version>LATEST</version>
</dependency>
```
2. 使用示例

继承类`tech.wetech.flexmodel.ApiDefinitionGenerator`
```groovy
class ListApiDefinitionGenerator extends ApiDefinitionGenerator {

  @Override
  def generate(PrintWriter out, GenerationContext context) {
    def modelName = context.getModelClass().getModelName()
    out.println "query MyListQuery( \$where: ${modelName}BoolExp) {"
    out.println "  ${modelName}(where: \$where) {"
    context.getModelClass().getAllFields().each {
      if (!it.isRelationField()) {
        out.println "    ${it.fieldName}"
      }
    }
    out.println "  }"
    out.println "}"
  }
}

```

通过模板输出到目标对象中
```java
ModelClass modelClass = GenerationTool.buildModelClass("com.example", dto.getDatasourceName(), entity);
GenerationContext generationContext = new GenerationContext();
generationContext.setModelClass(modelClass);
new ListApiDefinitionGenerator().generate(new PrintWriter(System.out), generationContext);
```
此为单文件的示例，`tech.wetech.flexmodel.codegen.AbstractModelListGenerator`实现多个模型输出到同一个文件中
