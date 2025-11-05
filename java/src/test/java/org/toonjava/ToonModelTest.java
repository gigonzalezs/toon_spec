package org.toonjava;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToonModelTest {

  @Test
  void storesAndRetrievesPrimitiveValues() {
    ToonObject object = new ToonObject();
    object.put("name", "Ada").put("active", true).put("score", 98.5d).put("count", 3);

    assertEquals("Ada", object.getString("name"));
    assertTrue(object.getBoolean("active"));
    assertEquals(98.5d, object.getDouble("score"));
    assertEquals(3, object.getInt("count"));
  }

  @Test
  void distinguishesNullFromMissingKeys() {
    ToonObject object = new ToonObject();
    object.put("nullable", null);

    assertTrue(object.has("nullable"));
    assertNull(object.get("nullable"));
    assertNull(object.opt("missing"));
    assertThrows(ToonException.class, () -> object.getString("nullable"));
    assertThrows(ToonException.class, () -> object.getString("missing"));
  }

  @Test
  void convertsMapsAndListsRecursively() {
    Map<String, Object> raw =
        new LinkedHashMap<>(
            Map.of("user", Map.of("name", "Ada"), "tags", List.of("ml", "ops"), "count", 2L));

    ToonObject object = new ToonObject(raw);
    ToonObject user = object.getObject("user");
    ToonArray tags = object.getArray("tags");

    assertEquals("Ada", user.getString("name"));
    assertEquals(List.of("ml", "ops"), tags.toList());
    assertEquals(2L, object.getLong("count"));
  }

  @Test
  void toMapAndToListProduceImmutableCopies() {
    ToonObject object = new ToonObject();
    object.put("name", "Ada").put("tags", new ToonArray(List.of("ml")));

    Map<String, Object> copy = object.toMap();
    assertEquals("Ada", copy.get("name"));

    @SuppressWarnings("unchecked")
    List<Object> tagsCopy = (List<Object>) copy.get("tags");
    assertEquals(List.of("ml"), tagsCopy);
    assertThrows(UnsupportedOperationException.class, () -> tagsCopy.add("ops"));

    object.getArray("tags").add("ops");
    assertEquals(List.of("ml"), tagsCopy, "la copia debe ser inmutable y aislada");
  }

  @Test
  void addAndRemoveFromToonArray() {
    ToonArray array = new ToonArray();
    array.add("a").add("b").add(0, "start");
    assertEquals(List.of("start", "a", "b"), array.toList());

    assertEquals("a", array.remove(1));
    array.set(1, "end");
    assertEquals(List.of("start", "end"), array.toList());
  }

  @Test
  void rejectsUnsupportedTypes() {
    ToonObject object = new ToonObject();
    Object unsupported = new Object();

    assertThrows(ToonException.class, () -> object.put("unsupported", unsupported));
    ToonArray array = new ToonArray();
    assertThrows(ToonException.class, () -> array.add(Double.NaN));
  }

  @Test
  void toonNullEqualityMatchesNull() {
    assertTrue(ToonNull.INSTANCE.equals(null));
    assertTrue(ToonNull.INSTANCE.equals(ToonNull.INSTANCE));
    ToonObject object = new ToonObject();
    object.put("value", ToonNull.INSTANCE);
    assertNull(object.get("value"));
  }
}
