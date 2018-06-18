package iguana;

import antlr4java.JavaLexer;
import antlr4java.JavaParser;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

public class AntlrJavaParser {

    private JavaLexer lexer;
    private JavaParser parser;

    public JavaParser.CompilationUnitContext parse(String input) {
        CharStream charStream = CharStreams.fromString(input);
        if (lexer == null) {
            lexer = new JavaLexer(charStream);
        } else {
            lexer.setInputStream(charStream);
        }

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        if (parser == null) {
            parser = new JavaParser(tokens);
        } else {
            parser.setTokenStream(tokens);
        }

        return parser.compilationUnit();
    }
}
