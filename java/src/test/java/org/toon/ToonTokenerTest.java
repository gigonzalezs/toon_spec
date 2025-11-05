package org.toon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToonTokenerTest {

  @Test
  void parsesFlatObject() {
    String source =
        String.join("\n", "id: 123", "name: \"Ada Lovelace\"", "active: true", "score: 98.5");

    ToonTokener tokener = new ToonTokener(source);
    Map<String, Object> object = tokener.nextObject();

    assertEquals(4, object.size());
    assertEquals(123L, object.get("id"));
    assertEquals("Ada Lovelace", object.get("name"));
    assertEquals(Boolean.TRUE, object.get("active"));
    assertEquals(98.5d, object.get("score"));
  }

  @Test
  void parsesArrayOfPrimitives() {
    String source = String.join("\n", "tags[3]:", "  - admin", "  - ops", "  - dev");

    ToonTokener tokener = new ToonTokener(source);
    List<Object> values = tokener.nextArray();

    assertEquals(List.of("admin", "ops", "dev"), values);
  }

  @Test
  void parsesObjectWithNestedArray() {
    String source =
        String.join("\n", "user:", "  name: Ada", "  tags[2]:", "    - admin", "    - ops");

    ToonTokener tokener = new ToonTokener(source);
    Map<String, Object> object = tokener.nextObject();

    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) object.get("user");
    assertNotNull(user);
    assertEquals("Ada", user.get("name"));
    @SuppressWarnings("unchecked")
    List<Object> tags = (List<Object>) user.get("tags");
    assertNotNull(tags);
    assertEquals(List.of("admin", "ops"), tags);
    assertFalse(tokener.hasMoreValues());
  }
}
