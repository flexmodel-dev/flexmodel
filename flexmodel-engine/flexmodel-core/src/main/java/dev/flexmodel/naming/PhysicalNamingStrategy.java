package dev.flexmodel.naming;

/**
 * @author cjbi
 */
public interface PhysicalNamingStrategy {

  String toPhysicalTableName(String tableName);

  String toPhysicalSequenceName(String sequenceName);

}
