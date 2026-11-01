package dev.flexmodel.sql.condition;

import dev.flexmodel.condition.ConditionNode;
import dev.flexmodel.condition.ConditionParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SqlConditionRendererTest {
  private final ConditionParser parser = new ConditionParser();

  @Test
  void rendersEqualityWithNullAsIsNull() {
    ConditionNode condition = parser.parse("{\"status\":{\"_eq\":null}}");
    SqlRenderContext context = new SqlRenderContext("`", new InlinePlaceholderHandler());
    assertEquals("`status` IS NULL", SqlConditionRenderer.render(condition, context));
  }

  @Test
  void rendersInequalityWithNullAsIsNotNull() {
    ConditionNode condition = parser.parse("{\"status\":{\"_ne\":null}}");
    SqlRenderContext context = new SqlRenderContext("`", new InlinePlaceholderHandler());
    assertEquals("`status` IS NOT NULL", SqlConditionRenderer.render(condition, context));
  }

  @Test
  void namedPlaceholdersStayEmptyForNullComparisons() {
    ConditionNode condition = parser.parse(
      "{\"_and\":[{\"category\":{\"_eq\":null}},{\"status\":{\"_ne\":null}}]}");
    NamedPlaceholderHandler placeholders = new NamedPlaceholderHandler();
    SqlRenderContext context = new SqlRenderContext("`", placeholders);
    assertEquals("(`category` IS NULL AND `status` IS NOT NULL)",
      SqlConditionRenderer.render(condition, context));
    assertTrue(placeholders.getParameters().isEmpty());
  }

  @Test
  void nullOrderingComparisonsNeverMatch() {
    ConditionNode condition = parser.parse("{\"amount\":{\"_gt\":null}}");
    SqlRenderContext context = new SqlRenderContext("`", new InlinePlaceholderHandler());
    assertEquals("1=0", SqlConditionRenderer.render(condition, context));
  }

  @Test
  void nullInsideInListStillRendersBoundParameter() {
    ConditionNode condition = parser.parse("{\"category\":{\"_in\":[\"a\",null]}}");
    NamedPlaceholderHandler placeholders = new NamedPlaceholderHandler();
    SqlRenderContext context = new SqlRenderContext("`", placeholders);
    assertEquals("`category` IN (:category_0, :category_1)",
      SqlConditionRenderer.render(condition, context));
    assertEquals(Map.of("category_0", "a"), Map.of("category_0", placeholders.getParameters().get("category_0")));
    assertTrue(placeholders.getParameters().containsKey("category_1"));
    assertNull(placeholders.getParameters().get("category_1"));
  }
}
