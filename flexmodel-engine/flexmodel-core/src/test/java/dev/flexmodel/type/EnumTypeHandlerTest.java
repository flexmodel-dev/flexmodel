package dev.flexmodel.type;

import dev.flexmodel.ModelRegistry;
import dev.flexmodel.model.EnumDefinition;
import dev.flexmodel.model.EnumElement;
import dev.flexmodel.model.field.EnumRefField;
import dev.flexmodel.model.field.Field;
import dev.flexmodel.model.SchemaObject;
import dev.flexmodel.session.AbstractSessionContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author cjbi
 */
public class EnumTypeHandlerTest {

  enum TestStatus {
    PENDING, PAID
  }

  static class StubRegistry implements ModelRegistry {
    final Map<String, SchemaObject> models = new HashMap<>();

    @Override
    public List<SchemaObject> loadFromDataSource(AbstractSessionContext sessionContext) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<SchemaObject> loadFromDataSource(AbstractSessionContext sessionContext, Set<String> includes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<SchemaObject> listRegistered(String schemaName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterAll(String schemaName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void unregisterAll(String schemaName, String modelName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void register(String schemaName, SchemaObject object) {
      models.put(object.getName(), object);
    }

    @Override
    public SchemaObject getRegistered(String schemaName, String modelName) {
      return models.get(modelName);
    }
  }

  private static EnumDefinition codedEnum() {
    EnumDefinition definition = new EnumDefinition("OrderStatus");
    definition.setElements(List.of(
      new EnumElement("PENDING", 1L, "待支付"),
      new EnumElement("PAID", 2L, null)));
    return definition;
  }

  private static EnumRefField field(boolean multiple) {
    EnumRefField field = new EnumRefField("status");
    field.setFrom("OrderStatus");
    field.setMultiple(multiple);
    return field;
  }

  private static EnumTypeHandler handler(EnumDefinition definition) {
    StubRegistry registry = new StubRegistry();
    registry.register("test", definition);
    return new EnumTypeHandler(registry, "test");
  }

  @Test
  void convertsNameToCodeOnWrite() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertEquals(1L, handler.convertParameter(field, "PENDING"));
    assertEquals(2L, handler.convertParameter(field, "PAID"));
    assertNull(handler.convertParameter(field, null));
  }

  @Test
  void normalizesEnumInstancesToCode() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertEquals(2L, handler.convertParameter(field, TestStatus.PAID));
  }

  @Test
  void convertsCodeToNameOnRead() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertEquals("PENDING", handler.getResult(field, 1L));
    assertEquals("PAID", handler.getResult(field, "2"));
  }

  @Test
  void fallsBackToStoredNameForLegacyRows() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertEquals("PENDING", handler.getResult(field, "PENDING"));
  }

  @Test
  void translatesMultipleValuesBothWays() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(true);
    assertEquals(List.of(1L, 2L), handler.convertParameter(field, List.of("PENDING", "PAID")));
    assertEquals(List.of("PENDING", "PAID"), handler.getResult(field, List.of(1L, 2L)));
  }

  @Test
  void rejectsUnknownNamesOnWrite() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertThrows(IllegalArgumentException.class, () -> handler.convertParameter(field, "UNKNOWN"));
  }

  @Test
  void rejectsUnknownCodesOnRead() {
    EnumTypeHandler handler = handler(codedEnum());
    Field field = field(false);
    assertThrows(IllegalArgumentException.class, () -> handler.getResult(field, "999"));
  }

  @Test
  void passesThroughWhenEnumHasNoCodes() {
    EnumDefinition definition = new EnumDefinition("PlainStatus");
    definition.setElements(List.of(new EnumElement("ACTIVE"), new EnumElement("INACTIVE")));
    EnumTypeHandler handler = handler(definition);
    Field field = field(false);
    assertEquals("ACTIVE", handler.convertParameter(field, "ACTIVE"));
    assertEquals("ACTIVE", handler.getResult(field, "ACTIVE"));
  }

  @Test
  void passesThroughWithoutRegistry() {
    EnumTypeHandler handler = new EnumTypeHandler();
    Field field = field(false);
    assertEquals("PENDING", handler.convertParameter(field, "PENDING"));
    assertEquals("PENDING", handler.getResult(field, "PENDING"));
  }
}