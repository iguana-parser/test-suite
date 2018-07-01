package iguana.benchmark;

import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.nio.file.Paths;

import static iguana.Utils.getFileContent;
import static iguana.Utils.newASTParser;

@State(Scope.Benchmark)
public class EclipseJDTBenchmark {

    @Param({""})
    private String path;

    private String input;

    @Setup
    public void init() throws IOException {
        input = getFileContent(Paths.get(path));
    }

    @Benchmark
    public CompilationUnit benchmark() {
        ASTParser parser = newASTParser(input);
        return (CompilationUnit) parser.createAST(null);
    }

}
