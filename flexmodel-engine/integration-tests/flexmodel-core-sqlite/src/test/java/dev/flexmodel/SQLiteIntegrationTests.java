package dev.flexmodel;

import dev.flexmodel.model.NativeQueryDefinition;
import dev.flexmodel.sql.JdbcSchemaProvider;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
