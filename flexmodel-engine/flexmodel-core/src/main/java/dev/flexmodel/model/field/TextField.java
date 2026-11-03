package dev.flexmodel.model.field;

/**
 * 大文本字段，对应数据库中的 TEXT / LONGTEXT / CLOB 等无固定长度的字符类型。
 * <p>
 * 与 {@link StringField} 的区别：
 * <ul>
 *   <li>没有长度限制，不参与行内存储的行长度计算（MySQL 下不会触发 65535 字节的行大小限制）</li>
 *   <li>不能作为普通索引、唯一约束的列（MySQL 需要前缀索引，Oracle/DB2/SQL Server 直接不支持），
 *   建模层会在创建时拒绝</li>
 * </ul>
 *
 * @author cjbi
 */
public class TextField extends TypedField<String, TextField> {

  public TextField(String name) {
    super(name, ScalarType.TEXT.getType());
  }

}
