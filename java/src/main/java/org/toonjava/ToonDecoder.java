package org.toonjava;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decodifica texto TOON a estructuras dinámicas {@link ToonObject} y {@link ToonArray}. Proporciona
 * utilidades para obtener representaciones listas para consumir por bibliotecas JSON estándar sin
 * introducir dependencias obligatorias.
 */
public final class ToonDecoder {
  private final ToonTokener tokener;

  public ToonDecoder(String source) {
    this(new ToonTokener(source));
  }

  public ToonDecoder(ToonTokener tokener) {
    this.tokener = Objects.requireNonNull(tokener, "tokener");
  }

  public boolean hasMoreValues() {
    return tokener.hasMoreValues();
  }

  public Object nextValue() {
    return wrap(tokener.nextValue());
  }

  public ToonObject nextObject() {
    return (ToonObject) wrap(tokener.nextObject());
  }

  public ToonArray nextArray() {
    return (ToonArray) wrap(tokener.nextArray());
  }

  public static Object decode(String source) {
    ToonDecoder decoder = new ToonDecoder(source);
    Object value = decoder.nextValue();
    if (decoder.hasMoreValues()) {
      throw new ToonException("Se encontraron valores adicionales después del valor principal");
    }
    return value;
  }

  public static ToonObject decodeObject(String source) {
    Object value = decode(source);
    if (value instanceof ToonObject object) {
      return object;
    }
    throw new ToonException("El texto TOON no representa un objeto en la raíz");
  }

  public static ToonArray decodeArray(String source) {
    Object value = decode(source);
    if (value instanceof ToonArray array) {
      return array;
    }
    throw new ToonException("El texto TOON no representa un array en la raíz");
  }

  public static Map<String, Object> decodeToMap(String source) {
    return decodeObject(source).toMap();
  }

  public static Object toJavaValue(Object value) {
    if (value == null || value == ToonNull.INSTANCE) {
      return null;
    }
    if (value instanceof ToonObject object) {
      return object.toMap();
    }
    if (value instanceof ToonArray array) {
      return array.toList();
    }
    return value;
  }

  public static Object toJsonNode(Object value) {
    Object plain = toJavaValue(value);
    return JacksonBridge.toJsonNode(plain);
  }

  private static Object wrap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return new ToonObject(castMap(map));
    }
    if (value instanceof List<?> list) {
      return new ToonArray(list);
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ?> castMap(Map<?, ?> map) {
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String)) {
        throw new ToonException(
            "Las claves deben ser String en objetos TOON: " + entry.getKey());
      }
    }
    return (Map<String, ?>) map;
  }

  private static final class JacksonBridge {
    private static final Object MAPPER;
    private static final Method VALUE_TO_TREE;

    static {
      Object mapper = null;
      Method method = null;
      try {
        Class<?> mapperClass = Class.forName("com.fasterxml.jackson.databind.ObjectMapper");
        mapper = mapperClass.getConstructor().newInstance();
        method = mapperClass.getMethod("valueToTree", Object.class);
      } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        mapper = null;
        method = null;
      } catch (InstantiationException
              | IllegalAccessException
              | InvocationTargetException
              | RuntimeException
          ex) {
        throw new ExceptionInInitializerError(ex);
      }
      MAPPER = mapper;
      VALUE_TO_TREE = method;
    }

    private JacksonBridge() {}

    static Object toJsonNode(Object value) {
      if (MAPPER == null || VALUE_TO_TREE == null) {
        throw new IllegalStateException(
            "Jackson databind no está disponible en el classpath para convertir a JsonNode");
      }
      try {
        return VALUE_TO_TREE.invoke(MAPPER, value);
      } catch (IllegalAccessException ex) {
        throw new ToonException(
            "No se pudo acceder a ObjectMapper#valueToTree para generar JsonNode", ex);
      } catch (InvocationTargetException ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        throw new ToonException(
            "Falló la conversión del valor TOON a JsonNode mediante Jackson", cause);
      }
    }
  }
}
