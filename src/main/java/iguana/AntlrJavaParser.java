package iguana;

import antlr4java.JavaLexer;
import antlr4java.JavaParser;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.BitSet;

public class AntlrJavaParser {

    public JavaParser.CompilationUnitContext parse(String input) {
        CharStream charStream = CharStreams.fromString(input);
        JavaLexer lexer = new JavaLexer(charStream);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new ThrowingErrorListener());
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new ThrowingErrorListener());
        return parser.compilationUnit();
    }


    static class ThrowingErrorListener extends BaseErrorListener {

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            System.out.println(msg + " line: " + line + " column: " + charPositionInLine);
            throw new ParseCancellationException();
        }

    }
}
