package iguana.benchmark;

import antlr4java.JavaParser;
import iguana.AntlrJavaParser;
import iguana.AntlrToJavaParseTreeVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Paths;

import static iguana.Utils.getFileContent;

@State(Scope.Benchmark)
public class AntlrBenchmark {

    @Param({""})
    private String path;

    private String input;

    private AntlrJavaParser parser;

    @Setup
    public void init() throws IOException {
        input = getFileContent(Paths.get(path));
        parser = new AntlrJavaParser();
    }

    @Benchmark
    public CompilationUnit benchmark() {
        JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
        return (CompilationUnit) compilationUnit.accept(new AntlrToJavaParseTreeVisitor());
    }
}
