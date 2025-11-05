package org.toon;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Contenedor tipo diccionario para valores TOON, inspirado en {@code JSONObject}. Conserva el orden
 * de inserción y valida los tipos almacenados.
 */
public final class ToonObject {
  private final LinkedHashMap<String, Object> values;

  public ToonObject() {
    this.values = new LinkedHashMap<>();
  }

  public ToonObject(Map<String, ?> source) {
    Objects.requireNonNull(source, "source");
    this.values = new LinkedHashMap<>(source.size());
    for (Map.Entry<String, ?> entry : source.entrySet()) {
      String key = Objects.requireNonNull(entry.getKey(), "key");
      this.values.put(key, canonicalize(entry.getValue()));
    }
  }

  public int size() {
    return values.size();
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public boolean has(String key) {
    Objects.requireNonNull(key, "key");
    return values.containsKey(key);
  }

  public ToonObject put(String key, Object value) {
    Objects.requireNonNull(key, "key");
    values.put(key, canonicalize(value));
    return this;
  }

  public Object get(String key) {
    Objects.requireNonNull(key, "key");
    if (!values.containsKey(key)) {
      throw new ToonException("Clave no encontrada: " + key);
    }
    return unwrap(values.get(key));
  }

  public Object opt(String key) {
    Objects.requireNonNull(key, "key");
    if (!values.containsKey(key)) {
      return null;
    }
    return unwrap(values.get(key));
  }

  public String getString(String key) {
    Object value = getRequired(key, "String");
    if (value instanceof String s) {
      return s;
    }
    throw typeError(key, "String", value);
  }

  public String optString(String key, String defaultValue) {
    Object value = opt(key);
    return value instanceof String s ? s : defaultValue;
  }

  public boolean getBoolean(String key) {
    Object value = getRequired(key, "Boolean");
    if (value instanceof Boolean b) {
      return b;
    }
    throw typeError(key, "Boolean", value);
  }

  public boolean optBoolean(String key, boolean defaultValue) {
    Object value = opt(key);
    return value instanceof Boolean b ? b : defaultValue;
  }

  public double getDouble(String key) {
    Object value = getRequired(key, "Number");
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    throw typeError(key, "Number", value);
  }

  public double optDouble(String key, double defaultValue) {
    Object value = opt(key);
    return value instanceof Number number ? number.doubleValue() : defaultValue;
  }

  public long getLong(String key) {
    Object value = getRequired(key, "Number");
    if (value instanceof Number number) {
      return number.longValue();
    }
    throw typeError(key, "Number", value);
  }

  public long optLong(String key, long defaultValue) {
    Object value = opt(key);
    return value instanceof Number number ? number.longValue() : defaultValue;
  }

  public int getInt(String key) {
    long number = getLong(key);
    if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
      throw new ToonException("Valor fuera de rango int para clave " + key + ": " + number);
    }
    return (int) number;
  }

  public int optInt(String key, int defaultValue) {
    Object value = opt(key);
    if (value instanceof Number number) {
      long longValue = number.longValue();
      if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
        return (int) longValue;
      }
    }
    return defaultValue;
  }

  public ToonObject getObject(String key) {
    Object value = getRequired(key, "ToonObject");
    if (value instanceof ToonObject obj) {
      return obj;
    }
    throw typeError(key, "ToonObject", value);
  }

  public ToonObject optObject(String key) {
    Object value = opt(key);
    return value instanceof ToonObject obj ? obj : null;
  }

  public ToonArray getArray(String key) {
    Object value = getRequired(key, "ToonArray");
    if (value instanceof ToonArray array) {
      return array;
    }
    throw typeError(key, "ToonArray", value);
  }

  public ToonArray optArray(String key) {
    Object value = opt(key);
    return value instanceof ToonArray array ? array : null;
  }

  public Object remove(String key) {
    Objects.requireNonNull(key, "key");
    Object previous = values.remove(key);
    return unwrap(previous);
  }

  public Set<String> keySet() {
    return Collections.unmodifiableSet(values.keySet());
  }

  public Collection<Object> values() {
    return Collections.unmodifiableCollection(toUnwrappedValues());
  }

  /**
   * Devuelve una copia del contenido como {@link Map}, deshaciendo centinelas y clonando estructuras
   * anidadas.
   */
  public Map<String, Object> toMap() {
    LinkedHashMap<String, Object> copy = new LinkedHashMap<>(values.size());
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      copy.put(entry.getKey(), cloneValue(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }

  @Override
  public String toString() {
    return toMap().toString();
  }

  private Object getRequired(String key, String expectedType) {
    Object value = opt(key);
    if (value == null && values.containsKey(key)) {
      throw new ToonException("El valor para '" + key + "' es null");
    }
    if (value == null) {
      throw new ToonException("Clave no encontrada: " + key);
    }
    return value;
  }

  private static ToonException typeError(String key, String expected, Object actual) {
    return new ToonException(
        "Se esperaba " + expected + " para '" + key + "' pero se encontró " + describe(actual));
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

  private static Object canonicalize(Object value) {
    if (value instanceof ToonNull || value == null) {
      return ToonNull.INSTANCE;
    }
    if (value instanceof ToonObject || value instanceof ToonArray) {
      return value;
    }
    if (value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Number number) {
      validateNumber(number);
      return number;
    }
    if (value instanceof Map<?, ?> map) {
      return new ToonObject(castMap(map));
    }
    if (value instanceof Iterable<?> iterable) {
      return new ToonArray(toList(iterable));
    }
    throw new ToonException(
        "Tipo de valor no soportado para ToonObject en clave desconocida: " + value.getClass());
  }

  private static java.util.List<Object> toList(Iterable<?> iterable) {
    java.util.ArrayList<Object> list = new java.util.ArrayList<>();
    for (Object item : iterable) {
      list.add(item);
    }
    return list;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ?> castMap(Map<?, ?> map) {
    for (Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new ToonException("Las claves deben ser String en ToonObject");
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

  private Collection<Object> toUnwrappedValues() {
    java.util.ArrayList<Object> list = new java.util.ArrayList<>(values.size());
    for (Object value : values.values()) {
      list.add(unwrap(value));
    }
    return list;
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
}
