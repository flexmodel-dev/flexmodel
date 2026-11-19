package dev.flexmodel;

import dev.flexmodel.model.NativeQueryDefinition;
import dev.flexmodel.model.field.DateTimeField;
import dev.flexmodel.model.field.DefaultValue;
import dev.flexmodel.model.field.StringField;
import dev.flexmodel.query.Expressions;
import dev.flexmodel.query.Query;
import dev.flexmodel.sql.JdbcSchemaProvider;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author cjbi
 */
public class SQLiteIntegrationTests extends AbstractSessionTests {

  @BeforeAll
  public static void beforeAll() throws Exception {
    AgroalDataSourceConfigurationSupplier cfg = new AgroalDataSourceConfigurationSupplier();
    cfg.connectionPoolConfiguration()
      .connectionFactoryConfiguration()
      .jdbcUrl("jdbc:sqlite:file::memory:?cache=shared");
    cfg.connectionPoolConfiguration().maxSize(5);
    AgroalDataSource dataSource = AgroalDataSource.from(cfg);
    initSession(new JdbcSchemaProvider("default", dataSource));
  }

  @Test
  void testNativeQuery() {
    String classesEntityName = "testNativeQueryClasses";
    createClassesEntity(classesEntityName);
    createClassesData(classesEntityName);
    @SuppressWarnings("all")
    List<Map> list = (List<Map>) session.data().executeNative(
      "select * from " + classesEntityName + " where id=${id} and className=${className} limit 10",
      Map.of("id", 3,
        "className", "二年级1班"));
    Assertions.assertFalse(list.isEmpty());
  }

  @Test
  void testNativeQueryModel() {
    String classesEntityName = "testNativeQueryModelClasses";
    createClassesEntity(classesEntityName);
    createClassesData(classesEntityName);
    String name = "testNativeQueryModel";
    NativeQueryDefinition model = new NativeQueryDefinition(name);
    model.setStatement("select * from " + classesEntityName + " where id=${id} and className=${className} limit 10");
    session.schema().createNativeQuery(model);
    List<Map<String, Object>> list = session.data().executeNativeQuery(name, Map.of("id", 3, "className", "二年级1班"));
    Assertions.assertFalse(list.isEmpty());
//    Assertions.assertNotNull(session.getAllModels());
  }

  @Test
  void testPartialUpdatePreservesCreationOrder() {
    String modelName = "testCellUpdateOrder";
    registerEntity(modelName);
    session.schema().createEntity(modelName, entity -> entity
      .addField(new StringField("id").asIdentity())
      .addField(new StringField("title"))
      .addField(new StringField("note").setNullable(true).setDefaultValue(DefaultValue.fixed("初始备注")))
      .addField(new DateTimeField("created_at").setDefaultValue(DefaultValue.NOW))
      .addField(new DateTimeField("updated_at").setDefaultValue(DefaultValue.NOW)));
    LocalDateTime olderCreatedAt = LocalDateTime.of(2025, 1, 1, 0, 0);
    LocalDateTime newerCreatedAt = olderCreatedAt.plusDays(1);
    LocalDateTime editedAt = newerCreatedAt.plusDays(1);
    session.data().insert(modelName, new HashMap<>(Map.of(
      "id", "older", "title", "旧记录", "note", "保留备注", "created_at", olderCreatedAt)));
    session.data().insert(modelName, new HashMap<>(Map.of(
      "id", "newer", "title", "新记录", "created_at", newerCreatedAt)));

    Query newestFirst = Query.Builder.create().orderBy(order -> order.desc("created_at").asc("id"))
      .page(1, 2).build();
    var before = session.data().findAll(modelName, newestFirst);
    Assertions.assertEquals(List.of("newer", "older"), before.stream().map(row -> row.get("id")).toList());
    Assertions.assertEquals("初始备注", before.getFirst().get("note"));

    session.dsl().update(modelName).values(Map.of("title", "已修改", "updated_at", editedAt)).whereId("older").execute();

    var after = session.data().findAll(modelName, newestFirst);
    Assertions.assertEquals(List.of("newer", "older"), after.stream().map(row -> row.get("id")).toList());
    Map<String, Object> edited = after.getLast();
    Assertions.assertEquals(olderCreatedAt, edited.get("created_at"));
    Assertions.assertEquals(editedAt, edited.get("updated_at"));
    Assertions.assertEquals("已修改", edited.get("title"));
    Assertions.assertEquals("保留备注", edited.get("note"));

    session.dsl().update(modelName).set("note", null).where(Expressions.field("id").eq("older")).execute();
    Map<String, Object> cleared = session.data().findById(modelName, "older").orElseThrow();
    Assertions.assertNull(cleared.get("note"));
    Assertions.assertEquals(olderCreatedAt, cleared.get("created_at"));
  }

  /**
   * 表已存在时再次应用 FML（重启场景）：新增字段必须通过 ALTER TABLE 补到物理表，
   * 否则查询报 no such column。
   */
  @Test
  void testApplyFmlAddsMissingColumn() {
    String modelName = "f_heal_test";
    registerEntity(modelName);
    session.applyFML("""
      model f_heal_test {
        id : String @id @length("64"),
        name : String @length("64"),
        @comment("自愈测试")
      }
      """);
    session.applyFML("""
      model f_heal_test {
        id : String @id @length("64"),
        name : String @length("64"),
        sort_order : Int @default("0"),
        @comment("自愈测试")
      }
      """);
    session.data().insert(modelName, new java.util.HashMap<>(Map.of("id", "1", "name", "n")));
    @SuppressWarnings("all")
    List<Map> rows = (List<Map>) session.data().executeNative("select sort_order from " + modelName, Map.of());
    Assertions.assertFalse(rows.isEmpty());
    Assertions.assertEquals(0, ((Number) rows.get(0).get("sort_order")).intValue());
  }

}
