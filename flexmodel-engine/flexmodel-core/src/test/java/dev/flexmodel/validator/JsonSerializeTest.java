package dev.flexmodel.validator;

import dev.flexmodel.model.field.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import dev.flexmodel.JsonUtils;

import java.util.List;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;

/**
 * @author cjbi
 */
public class JsonSerializeTest {

  @Test
  void test() {
    EntityDefinition entity = new EntityDefinition("students");
    // 主键
    LongField idField = new LongField("id");
    idField.setComment("Primary Key");
    entity.addField(new LongField("id").asIdentity().setComment("Primary Key"));
    entity.setComment("学生表");
    // 姓名
    StringField name = new StringField("name");
    name.setComment("姓名");
    name.setNullable(false);
    name.setLength(10);
    entity.addField(name);
    LongField age = new LongField("age");
    age.setComment("年龄");
    entity.addField(age);
    // 备注
    StringField description = new StringField("description");
    description.setComment("备注");
    entity.addField(description);
    // 生日
    DateField birthday = new DateField("birthday");
    birthday.setComment("生日");
    entity.addField(birthday);
    // 是否禁用
    BooleanField isLocked = new BooleanField("isLocked");
    isLocked.setNullable(false);
    isLocked.setFixedDefaultValue(false);
    isLocked.setComment("是否禁用");
    entity.addField(isLocked);
    // 创建时间
    DateTimeField createDatetime = new DateTimeField("createDatetime");
    createDatetime.setComment("创建日期时间");
    createDatetime.setNullable(false);
    entity.addField(createDatetime);
    // 扩展信息
    JSONField extra = new JSONField("extra");
    extra.setComment("扩展信息");
    entity.addField(extra);
    // 邮箱
    StringField email = new StringField("email");
    email.setComment("邮箱");
    email.setLength(20);
    entity.addField(email);

    String json = JsonUtils.toJsonString(entity);
    EntityDefinition deSerializeEntity = JsonUtils.parseToObject(json, EntityDefinition.class);
    Assertions.assertNotNull(deSerializeEntity);
  }


  @Test
  void enumElementCodesSurviveJsonRoundTrip() {
    EnumDefinition definition = new EnumDefinition("OrderStatus");
    definition.setElementDefinitions(java.util.List.of(
      new EnumElement("PENDING", 1L, "待支付"),
      new EnumElement("PAID", 2L, null)));

    String json = JsonUtils.toJsonString(definition);
    // 结构化枚举元素统一使用 elements 字段序列化
    Assertions.assertTrue(json.contains("\"elements\":[{\"name\":\"PENDING\""));
    Assertions.assertFalse(json.contains("\"elementDefinitions\""));

    EnumDefinition deserialized = JsonUtils.parseToObject(json, EnumDefinition.class);
    Assertions.assertEquals(List.of("PENDING", "PAID"), deserialized.getElements());
    Assertions.assertEquals(2, deserialized.getElementDefinitions().size());
    Assertions.assertEquals(1, ((Number) deserialized.getElementDefinitions().get(0).getCode()).intValue());
    Assertions.assertEquals("待支付", deserialized.getElementDefinitions().get(0).getComment());
    Assertions.assertEquals(2, ((Number) deserialized.getElementDefinitions().get(1).getCode()).intValue());

    // 旧版纯字符串 elements 输入仍然可用
    EnumDefinition legacy = JsonUtils.parseToObject(
      "{\"type\":\"Enum\",\"name\":\"PlainStatus\",\"elements\":[\"A\",\"B\"]}", EnumDefinition.class);
    Assertions.assertEquals(List.of("A", "B"), legacy.getElements());

    EnumDefinition legacyStructured = JsonUtils.parseToObject(
      "{\"type\":\"Enum\",\"name\":\"LegacyStatus\",\"elementDefinitions\":[{\"name\":\"A\",\"code\":1}]}",
      EnumDefinition.class);
    Assertions.assertEquals(List.of("A"), legacyStructured.getElements());
    Assertions.assertEquals(1, ((Number) legacyStructured.getElementDefinitions().get(0).getCode()).intValue());
  }
}
