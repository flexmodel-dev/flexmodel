package dev.flexmodel.codegen;

import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author cjbi
 */
public class EnumGeneratorTest {

  private static String generate(EnumDefinition definition) {
    EnumClass enumClass = EnumClass.buildEnumClass("com.example", "system", definition);
    GenerationContext context = new GenerationContext();
    context.setEnumClass(enumClass);
    StringWriter buffer = new StringWriter();
    new EnumGenerator().writeEnum(new PrintWriter(buffer), context);
    return buffer.toString().replace("\r\n", "\n");
  }

  @Test
  void writesElementCommentsAsJavadoc() {
    EnumDefinition definition = new EnumDefinition("OrderStatus");
    definition.setElementDefinitions(List.of(
      new EnumElement("PENDING", 1L, "待支付"),
      new EnumElement("PAID", 2L, "已支付"),
      new EnumElement("CLOSED", 9L, null)));
    String generated = generate(definition);

    assertTrue(generated.contains("/**"));
    assertTrue(generated.contains("* 待支付"));
    assertTrue(generated.contains("* 已支付"));
    assertTrue(generated.contains("PENDING,"));
    assertTrue(generated.contains("PAID,"));
    assertTrue(generated.contains("CLOSED\n"));
    assertFalse(generated.contains("PENDING, PAID"), "带注释时应逐行输出常量");
  }

  @Test
  void keepsCompactLayoutWithoutComments() {
    EnumDefinition definition = new EnumDefinition("PlainStatus");
    definition.setElements(List.of("ACTIVE", "INACTIVE", "PENDING"));
    String generated = generate(definition);

    assertTrue(generated.contains("    ACTIVE, INACTIVE, PENDING\n"),
      "无注释时保持原有紧凑格式，已生成代码零变更");
  }
}