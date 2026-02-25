package dev.flexmodel.session;

/**
 * Schema Not Found Exception
 * Thrown when attempting to create a session for a non-existent schema
 *
 * @author cjbi
 */
public class SchemaNotFoundException extends RuntimeException {

  public SchemaNotFoundException(String message) {
    super(message);
  }

  public SchemaNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public SchemaNotFoundException(Throwable cause) {
    super(cause);
  }
}
