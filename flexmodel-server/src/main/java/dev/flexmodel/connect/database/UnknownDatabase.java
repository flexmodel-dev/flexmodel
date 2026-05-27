package dev.flexmodel.connect.database;

/**
 * @author cjbi
 */
public class UnknownDatabase extends Database {
  @Override
  public String getDbKind() {
    return "UNKNOWN";
  }
}
