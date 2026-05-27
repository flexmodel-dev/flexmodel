package dev.flexmodel.connect.database;

/**
 * @author cjbi
 */
public class MongoDB extends Database {
  @Override
  public String getDbKind() {
    return "mongodb";
  }
}
