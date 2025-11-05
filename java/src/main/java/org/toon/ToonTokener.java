package org.toon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.toon.grammar.ToonLexer;
import org.toon.grammar.ToonParser;

/**
 * Tokener simple basado en líneas que envuelve el parser ANTLR de encabezados para construir
 * estructuras básicas (Map/List) a partir de texto TOON.
 *
 * <p>Implementa un subconjunto de la gramática suficiente para los snippets de validación inicial.
 * Se ampliará progresivamente en tareas posteriores.
 */
public final class ToonTokener {
  private static final Pattern KEY_VALUE_PATTERN = Pattern.compile("([^:]+):(.*)");
  private static final int INDENT_SIZE = 2;

  private final List<LineInfo> lines;
  private int index = 0;

  public ToonTokener(String source) {
    Objects.requireNonNull(source, "source");
    this.lines = normalizeLines(source);
  }

  public boolean hasMoreValues() {
    skipBlankLines();
    return index < lines.size();
  }

  public Object nextValue() {
    skipBlankLines();
    if (index >= lines.size()) {
      return null;
    }
    LineInfo current = peekLine();
    if (isArrayHeader(current)) {
      Header header = parseHeader(current.trimmed, current.lineNumber, current.indent);
      consumeLine();
      return readArray(header, current.indent + INDENT_SIZE);
    }
    return readObject(current.indent);
  }

  public Map<String, Object> nextObject() {
    skipBlankLines();
    if (index >= lines.size()) {
      throw error("No hay objeto disponible", currentLineNumber(), 1);
    }
    LineInfo current = peekLine();
    return readObject(current.indent);
  }

  public List<Object> nextArray() {
    skipBlankLines();
    if (index >= lines.size()) {
      throw error("No hay array disponible", currentLineNumber(), 1);
    }
    LineInfo current = peekLine();
    if (!isArrayHeader(current)) {
      throw error("Se esperaba encabezado de array", current.lineNumber, current.indent + 1);
    }
    Header header = parseHeader(current.trimmed, current.lineNumber, current.indent);
    consumeLine();
    return readArray(header, current.indent + INDENT_SIZE);
  }

  private Map<String, Object> readObject(int expectedIndent) {
    Map<String, Object> result = new LinkedHashMap<>();
    while (hasMoreValues()) {
      LineInfo line = peekLine();
      if (line.indent < expectedIndent) {
        break;
      }
      if (line.indent > expectedIndent) {
        throw error("Indentación inesperada", line.lineNumber, line.indent + 1);
      }

      if (isArrayHeader(line)) {
        Header header = parseHeader(line.trimmed, line.lineNumber, line.indent);
        consumeLine();
        List<Object> array = readArray(header, expectedIndent + INDENT_SIZE);
        if (header.key == null) {
          throw error(
              "Los encabezados de array dentro de objetos requieren una clave", line.lineNumber, 1);
        }
        result.put(header.key, array);
        continue;
      }

      consumeLine();
      Matcher matcher = KEY_VALUE_PATTERN.matcher(line.trimmed);
      if (!matcher.matches()) {
        throw error("Se esperaba par clave:valor", line.lineNumber, 1);
      }
      String key = matcher.group(1).trim();
      String valueSegment = matcher.group(2).trim();
      if (valueSegment.isEmpty()) {
        Map<String, Object> nested = readObject(expectedIndent + INDENT_SIZE);
        result.put(key, nested);
      } else {
        Object value =
            parsePrimitive(valueSegment, line.lineNumber, line.indent + line.raw.indexOf(':') + 2);
        result.put(key, value);
      }
    }
    return result;
  }

  private List<Object> readArray(Header header, int expectedIndent) {
    List<Object> items = new ArrayList<>();
    while (hasMoreValues()) {
      LineInfo line = peekLine();
      if (line.indent < expectedIndent) {
        break;
      }
      if (line.indent > expectedIndent) {
        throw error("Indentación inesperada en array", line.lineNumber, line.indent + 1);
      }
      if (!line.trimmed.startsWith("- ")) {
        throw error("Se esperaba elemento con '- '", line.lineNumber, line.indent + 1);
      }
      consumeLine();
      String payload = line.trimmed.substring(2).trim();
      if (payload.isEmpty()) {
        throw error("Elemento de array vacío", line.lineNumber, line.indent + 1);
      }

      if (payload.contains("[") && payload.endsWith(":")) {
        Header nestedHeader = parseHeader(payload, line.lineNumber, line.indent + 2);
        List<Object> nestedArray = readArray(nestedHeader, expectedIndent + INDENT_SIZE);
        items.add(nestedArray);
      } else if (payload.contains(":")) {
        // objeto en línea dentro del array
        Matcher matcher = KEY_VALUE_PATTERN.matcher(payload);
        if (!matcher.matches()) {
          throw error("Sintaxis de objeto inválida", line.lineNumber, line.indent + 1);
        }
        Map<String, Object> inline = new LinkedHashMap<>();
        String key = matcher.group(1).trim();
        String valueSegment = matcher.group(2).trim();
        if (valueSegment.isEmpty()) {
          Map<String, Object> nested = readObject(expectedIndent + INDENT_SIZE);
          inline.put(key, nested);
        } else {
          inline.put(
              key,
              parsePrimitive(
                  valueSegment, line.lineNumber, line.indent + line.raw.indexOf(':') + 2));
        }
        items.add(inline);
      } else {
        items.add(parsePrimitive(payload, line.lineNumber, line.indent + 3));
      }
    }
    if (header.length >= 0 && items.size() != header.length) {
      throw error(
          "El encabezado declara " + header.length + " elementos pero se leyeron " + items.size(),
          currentLineNumber(),
          1);
    }
    return items;
  }

  private Header parseHeader(String text, int line, int indent) {
    try {
      ToonLexer lexer = new ToonLexer(CharStreams.fromString(text));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      ToonParser parser = new ToonParser(tokens);
      ToonParser.HeaderContext ctx = parser.header();
      String key = null;
      if (ctx.key() != null) {
        if (ctx.key().UNQUOTED_KEY() != null) {
          key = ctx.key().UNQUOTED_KEY().getText();
        } else {
          key = unescape(ctx.key().STRING().getText(), line, indent + 1);
        }
      }
      int length = Integer.parseInt(ctx.bracketSegment().DIGITS().getText());
      return new Header(key, length);
    } catch (RuntimeException ex) {
      throw error("Encabezado inválido: " + text, line, indent + 1, ex);
    }
  }

  private void skipBlankLines() {
    while (index < lines.size() && lines.get(index).trimmed.isEmpty()) {
      index++;
    }
  }

  private LineInfo peekLine() {
    return lines.get(index);
  }

  private void consumeLine() {
    index++;
  }

  private int currentLineNumber() {
    return index < lines.size() ? lines.get(index).lineNumber : lines.size();
  }

  private static List<LineInfo> normalizeLines(String source) {
    String[] rawLines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    List<LineInfo> result = new ArrayList<>(rawLines.length);
    int lineNumber = 1;
    for (String raw : rawLines) {
      int indent = countIndent(raw, lineNumber);
      result.add(new LineInfo(raw, raw.trim(), indent, lineNumber));
      lineNumber++;
    }
    return result;
  }

  private static int countIndent(String raw, int lineNumber) {
    int count = 0;
    for (char ch : raw.toCharArray()) {
      if (ch == ' ') {
        count++;
      } else if (ch == '\t') {
        throw new ToonException(
            "La indentación con tabuladores no está permitida", lineNumber, count + 1);
      } else {
        break;
      }
    }
    if (count % INDENT_SIZE != 0) {
      throw new ToonException(
          "Indentación no válida, se esperaba múltiplo de " + INDENT_SIZE, lineNumber, count + 1);
    }
    return count;
  }

  private static Object parsePrimitive(String text, int line, int column) {
    if (text.equals("null")) {
      return null;
    }
    if (text.equals("true")) {
      return Boolean.TRUE;
    }
    if (text.equals("false")) {
      return Boolean.FALSE;
    }
    if (isQuoted(text)) {
      return unescape(text, line, column);
    }
    if (isNumber(text)) {
      try {
        if (text.contains(".") || text.contains("e") || text.contains("E")) {
          return Double.parseDouble(text);
        }
        return Long.parseLong(text);
      } catch (NumberFormatException ex) {
        throw new ToonException("Número inválido: " + text, line, column, ex);
      }
    }
    return text;
  }

  private static boolean isNumber(String text) {
    return text.matches("-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
  }

  private static boolean isQuoted(String text) {
    return text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"");
  }

  private static String unescape(String text, int line, int column) {
    StringBuilder sb = new StringBuilder(text.length() - 2);
    for (int i = 1; i < text.length() - 1; i++) {
      char ch = text.charAt(i);
      if (ch == '\\') {
        if (i + 1 >= text.length() - 1) {
          throw new ToonException("Secuencia de escape incompleta", line, column + i);
        }
        char next = text.charAt(++i);
        switch (next) {
          case '\\':
            sb.append('\\');
            break;
          case '"':
            sb.append('"');
            break;
          case 'n':
            sb.append('\n');
            break;
          case 'r':
            sb.append('\r');
            break;
          case 't':
            sb.append('\t');
            break;
          default:
            throw new ToonException("Escape inválido: \\" + next, line, column + i);
        }
      } else {
        sb.append(ch);
      }
    }
    return sb.toString();
  }

  private static boolean isArrayHeader(LineInfo line) {
    return line.trimmed.contains("[") && line.trimmed.endsWith(":");
  }

  private static ToonException error(String message, int line, int column) {
    return new ToonException(message, line, column);
  }

  private static ToonException error(String message, int line, int column, Throwable cause) {
    return new ToonException(message, line, column, cause);
  }

  private record LineInfo(String raw, String trimmed, int indent, int lineNumber) {}

  private static final class Header {
    final String key;
    final int length;

    Header(String key, int length) {
      this.key = key;
      this.length = length;
    }
  }
}
