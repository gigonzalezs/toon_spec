package org.toonjava;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.toonjava.grammar.ToonLexer;
import org.toonjava.grammar.ToonParser;

/**
 * Tokener sencillo que envuelve el parser ANTLR de encabezados para transformar texto TOON en
 * estructuras básicas de Java (Map/List). Soporta encabezados con valores inline, arrays tabulares
 * y objetos multi-línea dentro de arrays.
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
    HeaderLine headerLine = parseHeaderLine(current);
    if (headerLine != null && headerLine.header.key == null) {
      consumeLine();
      return readArray(headerLine, current.indent + INDENT_SIZE);
    }
    if (!current.trimmed.contains(":") || isQuoted(current.trimmed)) {
      consumeLine();
      return parsePrimitive(current.trimmed, current.lineNumber, current.indent + 1);
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
    HeaderLine headerLine = parseHeaderLine(current);
    if (headerLine == null || headerLine.header.key != null) {
      throw error("Se esperaba encabezado de array en la posición actual", current.lineNumber, 1);
    }
    consumeLine();
    return readArray(headerLine, current.indent + INDENT_SIZE);
  }

  private Map<String, Object> readObject(int expectedIndent) {
    Map<String, Object> result = new LinkedHashMap<>();
    readObjectEntries(result, expectedIndent);
    return result;
  }

  private void readObjectEntries(Map<String, Object> target, int expectedIndent) {
    boolean allowIndentAdjustment = target.isEmpty();
    while (index < lines.size()) {
      LineInfo line = peekLine();
      if (line.trimmed.isEmpty()) {
        // Blank lines fuera de arrays: se ignoran.
        consumeLine();
        continue;
      }
      if (line.indent < expectedIndent) {
        break;
      }
      if (line.indent > expectedIndent) {
        if (allowIndentAdjustment) {
          expectedIndent = line.indent;
        } else {
          throw error("Indentación inesperada", line.lineNumber, line.indent + 1);
        }
      }

      HeaderLine headerLine = parseHeaderLine(line);
      if (headerLine != null) {
        if (headerLine.header.key == null) {
          throw error(
              "Los encabezados de array dentro de objetos requieren una clave", line.lineNumber, 1);
        }
        consumeLine();
        target.put(headerLine.header.key, readArray(headerLine, expectedIndent + INDENT_SIZE));
        readObjectEntries(target, expectedIndent);
        allowIndentAdjustment = false;
        continue;
      }

      consumeLine();
      ParsedKeyValue kv = parseKeyValue(line.trimmed, line.lineNumber, line.indent + 1);
      if (kv.valueSegment.isEmpty()) {
        target.put(kv.key, readObject(expectedIndent + INDENT_SIZE));
      } else {
        target.put(kv.key, parsePrimitive(kv.valueSegment, line.lineNumber, kv.valueColumn));
      }
      allowIndentAdjustment = false;
    }
  }

  private List<Object> readArray(HeaderLine headerLine, int expectedIndent) {
    Header header = headerLine.header;
    List<Object> items = new ArrayList<>();

    if (!headerLine.inlineSegment.isEmpty()) {
      if (header.isTabular()) {
        Map<String, Object> row =
            parseTabularRow(
                headerLine.inlineSegment, header, headerLine.lineNumber, headerLine.inlineColumn);
        items.add(row);
      } else {
        List<TokenSlice> tokens =
            parseDelimitedValues(
                headerLine.inlineSegment,
                header.delimiter,
                headerLine.lineNumber,
                headerLine.inlineColumn);
        for (TokenSlice slice : tokens) {
          items.add(parsePrimitive(slice.text, headerLine.lineNumber, slice.column));
        }
      }
    }

    while (index < lines.size()) {
      LineInfo line = peekLine();
      if (line.indent < expectedIndent) {
        break;
      }
      if (line.trimmed.isEmpty()) {
        throw error(
            "Las líneas en blanco dentro de arrays no son válidas en modo estricto",
            line.lineNumber,
            line.indent + 1);
      }

      if (header.isTabular()) {
        if (line.indent == expectedIndent
            && line.trimmed.indexOf(header.delimiter) < 0
            && KEY_VALUE_PATTERN.matcher(line.trimmed).matches()) {
          break;
        }
        if (line.indent != expectedIndent) {
          throw error("Indentación inválida en fila tabular", line.lineNumber, line.indent + 1);
        }
        consumeLine();
        Map<String, Object> row =
            parseTabularRow(line.trimmed, header, line.lineNumber, line.indent + 1);
        items.add(row);
        continue;
      }

      if (!line.trimmed.startsWith("-")) {
        if (line.indent == expectedIndent) {
          break;
        }
        throw error(
            "Se esperaba elemento de array con prefijo '- '", line.lineNumber, line.indent + 1);
      }

      consumeLine();
      if (line.trimmed.length() > 1 && line.trimmed.charAt(1) != ' ') {
        throw error(
            "Se esperaba elemento de array con prefijo '- '", line.lineNumber, line.indent + 1);
      }
      String payload = line.trimmed.length() == 1 ? "" : line.trimmed.substring(2).trim();
      if (payload.isEmpty()) {
        items.add(new LinkedHashMap<>());
        continue;
      }

      HeaderLine nestedHeaderLine = parseHeaderText(payload, line.lineNumber, line.indent + 3);
      if (nestedHeaderLine != null) {
        List<Object> nested = readArray(nestedHeaderLine, expectedIndent + INDENT_SIZE);
        if (nestedHeaderLine.header.key != null) {
          Map<String, Object> inline = new LinkedHashMap<>();
          inline.put(nestedHeaderLine.header.key, nested);
          readObjectEntries(inline, expectedIndent + INDENT_SIZE);
          items.add(inline);
        } else {
          items.add(nested);
        }
        continue;
      }

      if (payload.contains(":")) {
        Map<String, Object> inline = new LinkedHashMap<>();
        ParsedKeyValue kv = parseKeyValue(payload, line.lineNumber, line.indent + 3);
        if (kv.valueSegment.isEmpty()) {
          inline.put(kv.key, readObject(expectedIndent + INDENT_SIZE));
        } else {
          inline.put(kv.key, parsePrimitive(kv.valueSegment, line.lineNumber, kv.valueColumn));
        }
        readObjectEntries(inline, expectedIndent + INDENT_SIZE);
        items.add(inline);
      } else {
        items.add(parsePrimitive(payload, line.lineNumber, line.indent + 3));
      }
    }

    if (header.length >= 0 && items.size() != header.length) {
      throw error(
          "El encabezado declara " + header.length + " elementos pero se leyeron " + items.size(),
          headerLine.lineNumber,
          1);
    }
    return items;
  }

  private Map<String, Object> parseTabularRow(
      String rowText, Header header, int line, int startColumn) {
    List<TokenSlice> slices = parseDelimitedValues(rowText, header.delimiter, line, startColumn);
    if (slices.size() != header.fields.size()) {
      throw error(
          "La fila tabular tiene "
              + slices.size()
              + " columnas pero se esperaban "
              + header.fields.size(),
          line,
          startColumn);
    }
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 0; i < slices.size(); i++) {
      TokenSlice slice = slices.get(i);
      row.put(header.fields.get(i), parsePrimitive(slice.text, line, slice.column));
    }
    return row;
  }

  private ParsedKeyValue parseKeyValue(String text, int line, int startColumn) {
    Matcher matcher = KEY_VALUE_PATTERN.matcher(text);
    if (!matcher.matches()) {
      throw error("Se esperaba par clave:valor", line, startColumn);
    }
    String keyToken = matcher.group(1).trim();
    String valueSegment = matcher.group(2).trim();

    int keyStartIndex = text.indexOf(keyToken);
    int keyColumn = startColumn + keyStartIndex;
    String key = decodeKey(keyToken, line, keyColumn);

    int colonIndex = text.indexOf(':');
    int valueIndex = colonIndex + 1;
    while (valueIndex < text.length() && Character.isWhitespace(text.charAt(valueIndex))) {
      valueIndex++;
    }
    int valueColumn = startColumn + valueIndex;
    return new ParsedKeyValue(key, valueSegment, valueColumn);
  }

  private String decodeKey(String token, int line, int column) {
    if (isQuoted(token)) {
      return unescape(token, line, column);
    }
    return token;
  }

  private HeaderLine parseHeaderLine(LineInfo line) {
    if (!line.trimmed.contains("[") || !line.trimmed.contains("]")) {
      return null;
    }
    return parseHeaderText(line.trimmed, line.lineNumber, line.indent + 1);
  }

  private HeaderLine parseHeaderText(String text, int lineNumber, int startColumn) {
    int colonIndex = text.indexOf(':');
    if (colonIndex < 0) {
      return null;
    }
    String headerSegment = text.substring(0, colonIndex + 1);
    if (!headerSegment.contains("[") || !headerSegment.contains("]")) {
      return null;
    }

    Header header = parseHeaderSegment(headerSegment, lineNumber, startColumn);
    String inlineSegment = text.substring(colonIndex + 1).trim();

    String tail = text.substring(colonIndex + 1);
    int offset = 0;
    while (offset < tail.length() && Character.isWhitespace(tail.charAt(offset))) {
      offset++;
    }
    int inlineColumn = startColumn + colonIndex + 1 + offset;
    return new HeaderLine(header, inlineSegment, lineNumber, inlineColumn);
  }

  private Header parseHeaderSegment(String headerText, int line, int startColumn) {
    try {
      ToonLexer lexer = new ToonLexer(CharStreams.fromString(headerText));
      CommonTokenStream tokens = new CommonTokenStream(lexer);
      ToonParser parser = new ToonParser(tokens);
      ToonParser.HeaderContext ctx = parser.header();

      String key = null;
      if (ctx.key() != null) {
        if (ctx.key().UNQUOTED_KEY() != null) {
          key = ctx.key().UNQUOTED_KEY().getText();
        } else {
          key = unescape(ctx.key().STRING().getText(), line, startColumn);
        }
      }

      String segment = ctx.bracketSegment().getText();
      int cursor = 1; // omite '['
      if (segment.charAt(cursor) == '#') {
        cursor++;
      }
      int lengthStart = cursor;
      while (cursor < segment.length() && Character.isDigit(segment.charAt(cursor))) {
        cursor++;
      }
      int length = Integer.parseInt(segment.substring(lengthStart, cursor));

      char delimiter = ',';
      if (segment.charAt(cursor) != ']') {
        delimiter = segment.charAt(cursor);
      }

      List<String> fields = new ArrayList<>();
      if (ctx.fieldsSegment() != null) {
        for (ToonParser.FieldNameContext fieldCtx : ctx.fieldsSegment().fieldName()) {
          String token;
          if (fieldCtx.key().UNQUOTED_KEY() != null) {
            token = fieldCtx.key().UNQUOTED_KEY().getText();
          } else {
            token = unescape(fieldCtx.key().STRING().getText(), line, startColumn);
          }
          fields.add(token);
        }
      }
      return new Header(key, length, delimiter, List.copyOf(fields));
    } catch (RuntimeException ex) {
      throw error("Encabezado inválido: " + headerText.trim(), line, startColumn, ex);
    }
  }

  private List<TokenSlice> parseDelimitedValues(
      String text, char delimiter, int line, int startColumn) {
    List<TokenSlice> tokens = new ArrayList<>();
    if (text.isEmpty()) {
      return tokens;
    }
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    int tokenStart = 0;
    int i = 0;
    while (i < text.length()) {
      char ch = text.charAt(i);
      if (ch == '\\' && inQuotes) {
        if (i + 1 >= text.length()) {
          throw error("Secuencia de escape incompleta", line, startColumn + i);
        }
        current.append(ch);
        i++;
        current.append(text.charAt(i));
        i++;
        continue;
      }
      if (ch == '"') {
        inQuotes = !inQuotes;
        current.append(ch);
        i++;
        continue;
      }
      if (ch == delimiter && !inQuotes) {
        tokens.add(finishToken(current, text, tokenStart, i, startColumn));
        current.setLength(0);
        i++;
        tokenStart = i;
        continue;
      }
      current.append(ch);
      i++;
    }
    if (inQuotes) {
      throw error("Cadena sin cerrar en lista delimitada", line, startColumn + text.length());
    }
    tokens.add(finishToken(current, text, tokenStart, text.length(), startColumn));
    return tokens;
  }

  private TokenSlice finishToken(
      StringBuilder current, String fullText, int tokenStart, int tokenEnd, int startColumn) {
    String raw = current.toString();
    int leading = 0;
    while (leading < raw.length() && Character.isWhitespace(raw.charAt(leading))) {
      leading++;
    }
    String trimmed = raw.substring(leading, raw.length()).trim();
    int column = startColumn + tokenStart + leading;
    return new TokenSlice(trimmed, column);
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
        long value = Long.parseLong(text);
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
          return (int) value;
        }
        return value;
      } catch (NumberFormatException ex) {
        throw new ToonException("Número inválido: " + text, line, column, ex);
      }
    }
    return text;
  }

  private static boolean isNumber(String text) {
    return text.matches("-?(0|[1-9]\\d*)(\\.\\d+)?([eE][+-]?\\d+)?");
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
    final char delimiter;
    final List<String> fields;

    Header(String key, int length, char delimiter, List<String> fields) {
      this.key = key;
      this.length = length;
      this.delimiter = delimiter;
      this.fields = fields;
    }

    boolean isTabular() {
      return !fields.isEmpty();
    }
  }

  private static final class HeaderLine {
    final Header header;
    final String inlineSegment;
    final int lineNumber;
    final int inlineColumn;

    HeaderLine(Header header, String inlineSegment, int lineNumber, int inlineColumn) {
      this.header = header;
      this.inlineSegment = inlineSegment;
      this.lineNumber = lineNumber;
      this.inlineColumn = inlineColumn;
    }
  }

  private record ParsedKeyValue(String key, String valueSegment, int valueColumn) {}

  private record TokenSlice(String text, int column) {}
}
