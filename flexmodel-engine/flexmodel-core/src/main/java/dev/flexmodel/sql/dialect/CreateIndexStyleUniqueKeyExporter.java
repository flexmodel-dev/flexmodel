package dev.flexmodel.sql.dialect;

import dev.flexmodel.sql.SqlColumn;
import dev.flexmodel.sql.SqlUniqueKey;
import dev.flexmodel.sql.StandardUniqueKeyExporter;

import java.util.StringJoiner;

/**
 * @author cjbi
 */
public class CreateIndexStyleUniqueKeyExporter extends StandardUniqueKeyExporter {

  public CreateIndexStyleUniqueKeyExporter(SqlDialect dialect) {
    super(dialect);
  }

  @Override
  public String[] getSqlCreateString(SqlUniqueKey uniqueKey) {
    StringJoiner columns = new StringJoiner(", ");
    for (SqlColumn sqlColumn : uniqueKey.getColumns()) {
      columns.add(sqlColumn.getQuotedName(sqlDialect));
    }
    return new String[]{"create unique index " + sqlDialect.quoteIdentifier(uniqueKey.getName()) +
                        " on " + sqlDialect.quoteIdentifier(uniqueKey.getTable().getName()) + " (" + columns + ")"};
  }

  @Override
  public String[] getSqlDropString(SqlUniqueKey uniqueKey) {
    return new String[]{"drop index " + sqlDialect.quoteIdentifier(uniqueKey.getName())};
  }
}
