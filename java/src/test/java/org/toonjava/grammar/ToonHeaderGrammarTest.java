package org.toonjava.grammar;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

class ToonHeaderGrammarTest {

  @Test
  void parsesRepresentativeHeadersFromSpec() {
    List<String> samples =
        List.of(
            "items[3]:",
            "items[#3]:",
            "items[3|]:",
            "items[3\t]:",
            "items[3]{id,name}:",
            "items[3|]{id|name|role}:",
            "\"my-key\"[2]:",
            "\"x-items\"[#2|]{\"id\"|\"display name\"}:");

    for (String sample : samples) {
      ParseTree tree = parseHeader(sample);
      assertTrue(tree instanceof ToonParser.HeaderContext, () -> "No se pudo parsear: " + sample);
    }
  }

  private ParseTree parseHeader(String input) {
    ToonLexer lexer = new ToonLexer(CharStreams.fromString(input));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    ToonParser parser = new ToonParser(tokens);

    CollectingErrorListener errorListener = new CollectingErrorListener(tokens);
    parser.removeErrorListeners();
    parser.addErrorListener(errorListener);

    ParseTree tree = parser.header();
    assertTrue(
        errorListener.errors.isEmpty(),
        () -> String.join(System.lineSeparator(), errorListener.errors));
    return tree;
  }

  private static final class CollectingErrorListener extends BaseErrorListener {
    private final List<String> errors = new ArrayList<>();
    private final TokenStream tokens;

    private CollectingErrorListener(TokenStream tokens) {
      this.tokens = tokens;
    }

    @Override
    public void syntaxError(
        Recognizer<?, ?> recognizer,
        Object offendingSymbol,
        int line,
        int charPositionInLine,
        String msg,
        RecognitionException e) {
      errors.add(
          "línea " + line + ":" + charPositionInLine + " " + msg + " → " + offendingInputSnippet());
    }

    private String offendingInputSnippet() {
      return tokens.getText();
    }
  }
}
