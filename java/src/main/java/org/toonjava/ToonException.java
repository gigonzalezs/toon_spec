package org.toonjava;

/** Excepción unchecked para errores de parseo y validación en TOON. */
public class ToonException extends RuntimeException {
  private final int line;
  private final int column;

  public ToonException(String message) {
    this(message, -1, -1, null);
  }

  public ToonException(String message, Throwable cause) {
    this(message, -1, -1, cause);
  }

  public ToonException(String message, int line, int column) {
    this(message, line, column, null);
  }

  public ToonException(String message, int line, int column, Throwable cause) {
    super(formatMessage(message, line, column), cause);
    this.line = line;
    this.column = column;
  }

  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  private static String formatMessage(String message, int line, int column) {
    if (line < 0) {
      return message;
    }
    if (column < 0) {
      return message + " (línea " + line + ")";
    }
    return message + " (línea " + line + ", columna " + column + ")";
  }
}
