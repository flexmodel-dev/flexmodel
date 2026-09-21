package dev.flexmodel.model.field;

import dev.flexmodel.JsonUtils;
import dev.flexmodel.parser.ASTNodeConverter;
import dev.flexmodel.model.EntityDefinition;
import dev.flexmodel.parser.impl.ModelParser;
import dev.flexmodel.parser.impl.ParseException;
import dev.flexmodel.sql.SqlColumn;
import dev.flexmodel.sql.SqlTable;
import dev.flexmodel.sql.StandardTableExporter;
import dev.flexmodel.sql.dialect.MariaDBSqlDialect;
import dev.flexmodel.sql.dialect.MySQLSqlDialect;
import dev.flexmodel.sql.dialect.OracleSqlDialect;
import dev.flexmodel.sql.dialect.PostgreSQLSqlDialect;
import dev.flexmodel.sql.dialect.SQLServerSqlDialect;
import dev.flexmodel.sql.dialect.SQLiteSqlDialect;
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringFieldTextTest {

  private EntityDefinition parseModel(String fml) throws ParseException {
    return (EntityDefinition) ASTNodeConverter.parseFML(fml).getModels().getFirst();
  }

  @Test
  void parseTextStringWithoutLength() throws ParseException {
    EntityDefinition entity = parseModel("""
      model Post {
        id: String @id,
        description?: String @text,
      }
      """);
    StringField field = (StringField) entity.getField("description");
    assertTrue(field.isText());
    assertEquals(255, field.getLength());
  }

  @Test
  void parseTextStringWithLength() throws ParseException {
    EntityDefinition entity = parseModel("""
      model Post {
        id: String @id,
        description?: String @text @length(10000),
      }
      """);

    StringField field = (StringField) entity.getField("description");
    assertTrue(field.isText());
    assertEquals(10000, field.getLength());
  }

  @Test
  void serializeTextStringBackToFml() throws ParseException {
    EntityDefinition entity = parseModel("""
      model Post {
        id: String @id,
        description?: String @text @length(10000),
      }
      """);

    ModelParser.Model model = (ModelParser.Model) ASTNodeConverter.fromSchemaObject(entity);
    assertNotNull(model);
    String fml = model.toString();
    assertTrue(fml.contains("description? : String @text @length(\"10000\")"));
  }

  @Test
  void stringFieldsRemainEqualOnlyWhenTextMatches() {
    StringField plain = new StringField("description").setText(false);
    StringField text = new StringField("description").setText(true);
    StringField sameText = new StringField("description").setText(true);

    assertNotEquals(plain, text);
    assertNotEquals(text, plain);
    assertEquals(text, sameText);
  }

  @Test
  void jsonSerializationRetainsTextFlag() {
    StringField field = new StringField("description").setText(true).setLength(10000);
    String json = JsonUtils.toJsonString(field);

    assertTrue(json.contains("\"text\":true"));
    StringField parsedField = JsonUtils.parseToObject(json, StringField.class);
    assertNotNull(parsedField);
    assertTrue(parsedField.isText());
  }

  @Test
  void textColumnUsesUnlimitedDdlType() {
    SqlTable table = new SqlTable();
    table.setName("post");

    SqlColumn nameColumn = new SqlColumn();
    nameColumn.setName("name");
    nameColumn.setSqlTypeCode(Types.VARCHAR);
    nameColumn.setLength(255);
    table.addColumn(nameColumn);

    SqlColumn descriptionColumn = new SqlColumn();
    descriptionColumn.setName("description");
    descriptionColumn.setSqlTypeCode(Types.LONGVARCHAR);
    table.addColumn(descriptionColumn);

    String createTable = new StandardTableExporter(new PostgreSQLSqlDialect())
      .getSqlCreateString(table)[0];

    assertTrue(createTable.contains("name varchar(255)"));
    assertTrue(createTable.contains("description text"));
  }

  @Test
  void dialectsMapLongVarcharToUnlimitedTextTypes() {
    assertEquals("longtext", new MySQLSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("longtext", new MariaDBSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("text", new PostgreSQLSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("varchar(MAX)", new SQLServerSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("clob", new OracleSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("text", new SQLiteSqlDialect().getTypeName(Types.LONGVARCHAR, 0, 0, 0));
  }
}
