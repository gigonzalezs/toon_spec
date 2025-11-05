package org.toon;

/**
 * Centinela que representa un valor {@code null} explícito dentro de un {@link ToonObject} o
 * {@link ToonArray}. Este enfoque permite distinguir entre la ausencia de una clave y un valor
 * nulo almacenado intencionalmente.
 */
public final class ToonNull {
  public static final ToonNull INSTANCE = new ToonNull();

  private ToonNull() {}

  /**
   * Devuelve {@code true} si el valor proporcionado representa un nulo explícito (ya sea porque es
   * literalmente {@code null} o porque utiliza el centinela {@link ToonNull}).
   */
  public static boolean isNull(Object value) {
    return value == null || value == INSTANCE;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == null || obj == this;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public String toString() {
    return "null";
  }
}
