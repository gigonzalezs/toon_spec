package org.toonjava;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToonDecoderFixtureTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FIXTURE_BASE = Path.of(".", "src", "test", "resources", "fixtures", "decode");

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtureCases")
  void decodeFixtures(
      String displayName, String input, JsonNode expected, boolean shouldError, boolean isObject) {
    if (shouldError) {
      assertThrows(ToonException.class, () -> ToonDecoder.decode(input), displayName);
      return;
    }

    Object decoded = ToonDecoder.decode(input);
    Object plain = ToonDecoder.toJavaValue(decoded);
    JsonNode actualNode = MAPPER.valueToTree(plain);

    assertEquals(expected, actualNode, () -> describeMismatch(displayName, expected, actualNode));

    Object jsonNode = ToonDecoder.toJsonNode(decoded);
    assertTrue(
        jsonNode instanceof JsonNode,
        () -> displayName + " -> la conversión a JsonNode devolvió " + jsonNode);
    assertEquals(expected, jsonNode, () -> describeMismatch(displayName, expected, jsonNode));

    if (isObject) {
      JsonNode mapNode = MAPPER.valueToTree(ToonDecoder.decodeToMap(input));
      assertEquals(expected, mapNode, () -> describeMismatch(displayName, expected, mapNode));
    }
  }

  private static Stream<Arguments> fixtureCases() throws IOException {
    if (!Files.isDirectory(FIXTURE_BASE)) {
      throw new IllegalStateException("No se encontró el directorio de fixtures: " + FIXTURE_BASE);
    }

    return Files.list(FIXTURE_BASE)
        .filter(Files::isRegularFile)
        .sorted(Comparator.comparing(Path::getFileName))
        .flatMap(ToonDecoderFixtureTest::readFixture);
  }

  private static Stream<Arguments> readFixture(Path path) {
    try {
      JsonNode root = MAPPER.readTree(Files.readString(path));
      JsonNode tests = root.get("tests");
      if (tests == null || !tests.isArray()) {
        throw new IllegalStateException("Fixture sin array de tests: " + path);
      }
      return StreamSupport.stream(tests.spliterator(), false)
          .map(test -> toArguments(path, test));
    } catch (IOException ex) {
      throw new IllegalStateException("No se pudo leer el fixture " + path, ex);
    }
  }

  private static Arguments toArguments(Path path, JsonNode testNode) {
    String name = testNode.path("name").asText("(sin nombre)");
    String displayName = path.getFileName() + " :: " + name;
    String input = testNode.path("input").asText();
    JsonNode expected = testNode.get("expected");
    boolean shouldError = testNode.path("shouldError").asBoolean(false);
    boolean isObject = expected != null && expected.isObject();
    return Arguments.of(displayName, input, expected, shouldError, isObject);
  }

  private static String describeMismatch(String displayName, JsonNode expected, Object actual) {
    String actualType = actual.getClass().getSimpleName();
    String expectedType = expected.getNodeType().toString();
    return displayName + " -> esperado: " + expected + "(" + expectedType + "), obtenido: " + actual + "(" + actualType
        + ")";
  }

  private ToonDecoderFixtureTest() {
  }
}
