package dev.flexmodel.sql.dialect;

import dev.flexmodel.sql.SqlColumn;
import dev.flexmodel.sql.SqlTable;
import dev.flexmodel.sql.StandardTableExporter;
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MySQL DDL 生成相关测试。
 *
 * @author cjbi
 */
class MySQLSqlDialectTest {

  private final MySQLSqlDialect dialect = new MySQLSqlDialect();

  @Test
  void longVarcharMapsToLongtextRegardlessOfLength() {
    assertEquals("longtext", dialect.getTypeName(Types.LONGVARCHAR, 0, 0, 0));
    assertEquals("longtext", dialect.getTypeName(Types.LONGVARCHAR, 255, 0, 0));
    assertEquals("varchar(255)", dialect.getTypeName(Types.VARCHAR, 255, 0, 0));
  }

  /**
   * 回归：物料主数据这类宽表，多个 varchar(4000) 会超过 InnoDB 65535 字节的行长度限制；
   * Text 字段必须生成 longtext（行内只占指针），否则 f_model_registry 之外的业务表创建失败。
   */
  @Test
  void textColumnsDoNotConsumeRowSizeBudget() {
    SqlTable table = new SqlTable();
    table.setName("plm_part");
    table.addColumn(column("id", Types.VARCHAR, 64));
    table.addColumn(column("owner_id", Types.VARCHAR, 64));
    table.addColumn(column("name", Types.VARCHAR, 255));
    table.addColumn(column("part_code", Types.VARCHAR, 255));
    table.addColumn(column("barcode", Types.VARCHAR, 512));
    table.addColumn(column("keywords", Types.VARCHAR, 512));
    table.addColumn(column("photo", Types.LONGVARCHAR, 255));
    table.addColumn(column("model_3d", Types.LONGVARCHAR, 255));
    table.addColumn(column("remark", Types.LONGVARCHAR, 255));

    String ddl = new StandardTableExporter(dialect).getSqlCreateString(table)[0];

    assertTrue(ddl.contains("photo longtext"), ddl);
    assertTrue(ddl.contains("model_3d longtext"), ddl);
    assertTrue(ddl.contains("remark longtext"), ddl);
    assertTrue(ddl.contains("name varchar(255)"), ddl);

    // 只有 varchar 列计入行内长度（utf8mb4 每字符最多 4 字节）
    long inlineBytes = (64 + 64 + 255 + 255 + 512 + 512) * 4L;
    assertTrue(inlineBytes < 65535, "行内字节数应低于 MySQL 的行长度上限");
  }

  private SqlColumn column(String name, int sqlTypeCode, int length) {
    SqlColumn column = new SqlColumn();
    column.setName(name);
    column.setTableName("plm_part");
    column.setSqlTypeCode(sqlTypeCode);
    column.setLength(length);
    return column;
  }
}
