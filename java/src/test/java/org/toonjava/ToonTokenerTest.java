package org.toonjava;

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
    assertEquals(123, object.get("id"));
    assertEquals("Ada Lovelace", object.get("name"));
    assertEquals(Boolean.TRUE, object.get("active"));
    assertEquals(98.5d, object.get("score"));
  }

  @Test
  void parsesInlinePrimitiveArrayWithinObject() {
    String source = "tags[3]: admin,ops,\"dev,sec\"";

    ToonTokener tokener = new ToonTokener(source);
    Map<String, Object> object = tokener.nextObject();

    @SuppressWarnings("unchecked")
    List<Object> tags = (List<Object>) object.get("tags");
    assertNotNull(tags);
    assertEquals(List.of("admin", "ops", "dev,sec"), tags);
  }

  @Test
  void parsesArrayOfPrimitives() {
    String source = String.join("\n", "[3]:", "  - admin", "  - ops", "  - dev");

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

  @Test
  void parsesTabularArray() {
    String source =
        String.join(
            "\n",
            "users[3]{id,name,role,active}:",
            "  1,Alice,admin,true",
            "  2,Bob,developer,true",
            "  3,Charlie,designer,false");

    ToonTokener tokener = new ToonTokener(source);
    Map<String, Object> object = tokener.nextObject();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> users = (List<Map<String, Object>>) object.get("users");
    assertNotNull(users);
    assertEquals(3, users.size());
    assertEquals(1, users.get(0).get("id"));
    assertEquals("Charlie", users.get(2).get("name"));
    assertEquals(Boolean.FALSE, users.get(2).get("active"));
  }

  @Test
  void parsesArrayWithInlineObjects() {
    String source =
        String.join(
            "\n",
            "[2]:",
            "  - id: 1",
            "    name: Ada",
            "    skills[2]: python,ml",
            "  - id: 2",
            "    name: Bob");

    ToonTokener tokener = new ToonTokener(source);
    List<Object> items = tokener.nextArray();
    assertEquals(2, items.size());

    @SuppressWarnings("unchecked")
    Map<String, Object> first = (Map<String, Object>) items.get(0);
    assertEquals(1, first.get("id"));
    assertEquals("Ada", first.get("name"));
    @SuppressWarnings("unchecked")
    List<Object> skills = (List<Object>) first.get("skills");
    assertNotNull(skills);
    assertEquals(List.of("python", "ml"), skills);

    @SuppressWarnings("unchecked")
    Map<String, Object> second = (Map<String, Object>) items.get(1);
    assertEquals("Bob", second.get("name"));
  }
}
