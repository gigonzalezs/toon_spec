package org.toon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Representa una lista ordenada de valores TOON. Inspirada en {@code JSONArray} de org.json pero
 * con validaciones estrictas de tipos compatibles.
 */
public final class ToonArray implements Iterable<Object> {
  private final List<Object> values;

  public ToonArray() {
    this.values = new ArrayList<>();
  }

  public ToonArray(List<?> source) {
    Objects.requireNonNull(source, "source");
    this.values = new ArrayList<>(source.size());
    for (Object value : source) {
      this.values.add(canonicalize(value));
    }
  }

  /**
   * Devuelve el número de elementos almacenados.
   *
   * @return tamaño actual del array.
   */
  public int size() {
    return values.size();
  }

  /**
   * Devuelve {@code true} cuando no hay elementos.
   */
  public boolean isEmpty() {
    return values.isEmpty();
  }

  /**
   * Obtiene el elemento en la posición indicada, lanzando {@link ToonException} si el índice es
   * inválido.
   */
  public Object get(int index) {
    Object value = opt(index);
    if (value == null && !containsIndex(index)) {
      throw new ToonException("Índice fuera de rango: " + index);
    }
    return value;
  }

  /**
   * Obtiene el elemento en la posición indicada o {@code null} si no existe.
   */
  public Object opt(int index) {
    if (!containsIndex(index)) {
      return null;
    }
    return unwrap(values.get(index));
  }

  public String getString(int index) {
    Object value = getRequired(index, "String");
    if (value instanceof String s) {
      return s;
    }
    throw typeError(index, "String", value);
  }

  public String optString(int index, String defaultValue) {
    Object value = opt(index);
    return value instanceof String s ? s : defaultValue;
  }

  public boolean getBoolean(int index) {
    Object value = getRequired(index, "Boolean");
    if (value instanceof Boolean b) {
      return b;
    }
    throw typeError(index, "Boolean", value);
  }

  public boolean optBoolean(int index, boolean defaultValue) {
    Object value = opt(index);
    return value instanceof Boolean b ? b : defaultValue;
  }

  public double getDouble(int index) {
    Object value = getRequired(index, "Number");
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    throw typeError(index, "Number", value);
  }

  public double optDouble(int index, double defaultValue) {
    Object value = opt(index);
    return value instanceof Number number ? number.doubleValue() : defaultValue;
  }

  public long getLong(int index) {
    Object value = getRequired(index, "Number");
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw typeError(index, "Number", value);
  }

  public long optLong(int index, long defaultValue) {
    Object value = opt(index);
    return value instanceof Number number ? number.longValue() : defaultValue;
  }

  public int getInt(int index) {
    long value = getLong(index);
    if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
      throw new ToonException("Valor fuera de rango int en índice " + index + ": " + value);
    }
    return (int) value;
  }

  public int optInt(int index, int defaultValue) {
    Object value = opt(index);
    if (value instanceof Number number) {
      long longValue = number.longValue();
      if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
        return (int) longValue;
      }
    }
    return defaultValue;
  }

  public ToonObject getObject(int index) {
    Object value = getRequired(index, "ToonObject");
    if (value instanceof ToonObject obj) {
      return obj;
    }
    throw typeError(index, "ToonObject", value);
  }

  public ToonArray getArray(int index) {
    Object value = getRequired(index, "ToonArray");
    if (value instanceof ToonArray array) {
      return array;
    }
    throw typeError(index, "ToonArray", value);
  }

  public ToonObject optObject(int index) {
    Object value = opt(index);
    return value instanceof ToonObject obj ? obj : null;
  }

  public ToonArray optArray(int index) {
    Object value = opt(index);
    return value instanceof ToonArray array ? array : null;
  }

  /**
   * Añade un valor al final del array. Devuelve la propia instancia para permitir encadenamiento.
   */
  public ToonArray add(Object value) {
    values.add(canonicalize(value));
    return this;
  }

  /**
   * Inserta un valor en la posición indicada, desplazando el resto de elementos hacia la derecha.
   */
  public ToonArray add(int index, Object value) {
    checkInsertIndex(index);
    values.add(index, canonicalize(value));
    return this;
  }

  /**
   * Reemplaza el valor existente en la posición indicada.
   */
  public ToonArray set(int index, Object value) {
    ensureIndex(index);
    values.set(index, canonicalize(value));
    return this;
  }

  /**
   * Elimina el elemento en la posición indicada y lo devuelve (sin envolver).
   */
  public Object remove(int index) {
    ensureIndex(index);
    return unwrap(values.remove(index));
  }

  /**
   * Expone una copia inmodificable del contenido, deshaciendo los centinelas internos.
   */
  public List<Object> toList() {
    List<Object> copy = new ArrayList<>(values.size());
    for (Object value : values) {
      copy.add(cloneValue(value));
    }
    return Collections.unmodifiableList(copy);
  }

  @Override
  public Iterator<Object> iterator() {
    return new Iterator<>() {
      private final Iterator<Object> delegate = values.iterator();

      @Override
      public boolean hasNext() {
        return delegate.hasNext();
      }

      @Override
      public Object next() {
        return unwrap(delegate.next());
      }

      @Override
      public void remove() {
        delegate.remove();
      }
    };
  }

  @Override
  public void forEach(Consumer<? super Object> action) {
    Objects.requireNonNull(action, "action");
    for (Object value : values) {
      action.accept(unwrap(value));
    }
  }

  @Override
  public String toString() {
    return toList().toString();
  }

  private boolean containsIndex(int index) {
    return index >= 0 && index < values.size();
  }

  private void ensureIndex(int index) {
    if (!containsIndex(index)) {
      throw new ToonException("Índice fuera de rango: " + index);
    }
  }

  private void checkInsertIndex(int index) {
    if (index < 0 || index > values.size()) {
      throw new ToonException("Índice de inserción fuera de rango: " + index);
    }
  }

  private static Object canonicalize(Object value) {
    if (value instanceof ToonNull || value == null) {
      return ToonNull.INSTANCE;
    }
    if (value instanceof String
        || value instanceof Boolean
        || value instanceof ToonObject
        || value instanceof ToonArray) {
      return value;
    }
    if (value instanceof Number number) {
      validateNumber(number);
      return number;
    }
    if (value instanceof List<?> list) {
      return new ToonArray(list);
    }
    if (value instanceof java.util.Map<?, ?> map) {
      return new ToonObject(castMap(map));
    }
    throw new ToonException("Tipo de valor no soportado en ToonArray: " + value.getClass());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ?> castMap(java.util.Map<?, ?> map) {
    for (Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new ToonException("Las claves deben ser String en ToonArray");
      }
    }
    return (Map<String, ?>) map;
  }

  private static void validateNumber(Number number) {
    if (number instanceof Double d && (d.isNaN() || d.isInfinite())) {
      throw new ToonException("Los números NaN o infinitos no están permitidos: " + number);
    }
    if (number instanceof Float f && (f.isNaN() || f.isInfinite())) {
      throw new ToonException("Los números NaN o infinitos no están permitidos: " + number);
    }
  }

  private static Object unwrap(Object value) {
    return value == ToonNull.INSTANCE ? null : value;
  }

  private static Object cloneValue(Object value) {
    if (value == ToonNull.INSTANCE) {
      return null;
    }
    if (value instanceof ToonObject obj) {
      return obj.toMap();
    }
    if (value instanceof ToonArray array) {
      return array.toList();
    }
    return value;
  }

  private Object getRequired(int index, String expected) {
    Object value = opt(index);
    if (value == null && containsIndex(index)) {
      throw new ToonException("El valor en índice " + index + " es null");
    }
    if (value == null) {
      throw new ToonException("Valor no encontrado en índice " + index);
    }
    return value;
  }

  private static ToonException typeError(int index, String expected, Object value) {
    return new ToonException(
        "Se esperaba "
            + expected
            + " en índice "
            + index
            + " pero se encontró "
            + describe(value));
  }

  private static String describe(Object value) {
    if (value == null) {
      return "null";
    }
    if (value == ToonNull.INSTANCE) {
      return "ToonNull";
    }
    return value.getClass().getSimpleName();
  }
}
