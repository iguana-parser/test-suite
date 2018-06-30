package iguana;

import antlr4java.JavaParser;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static iguana.FileUtils.getFileContent;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE;
import static org.junit.Assert.assertTrue;

public class TestASTs {

    private AntlrJavaParser parser = new AntlrJavaParser();
    private Map<String, String> options = new HashMap<>();
    private Set<String> exclude = new HashSet<>();

    @BeforeEach
    void init() {
        options.put(COMPILER_SOURCE, "1.7");
        exclude.add("/Users/Ali/workspace-thesis/jdk7u-jdk/test/demo/jvmti/DemoRun.java");
    }

    @Test
    public void testAllInOne7() throws Exception {
        URI uri = TestASTs.class.getClassLoader().getResource("Test.java").toURI();
        String input = getFileContent(Paths.get(uri));
        JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
        CompilationUnit antlrResult = (CompilationUnit) compilationUnit.accept(new ToJavaParseTreeVisitor());

        ASTParser jdkParser = ASTParser.newParser(AST.JLS10);
        jdkParser.setCompilerOptions(options);
        jdkParser.setSource(input.toCharArray());
        jdkParser.setKind(ASTParser.K_COMPILATION_UNIT);
        CompilationUnit jdtResult = (CompilationUnit) jdkParser.createAST(null);

        assertTrue(antlrResult.subtreeMatch(new CustomASTMatcher(), jdtResult));
    }

    public void testJdk7() throws Exception {
        List<Path> javaFiles = Files.walk(Paths.get("/Users/Ali/workspace-thesis/jdk7u-jdk"))
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".java"))
                .collect(toList());

        for (Path path : javaFiles) {
            System.out.println(path);
            String input = getFileContent(path);
            JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
            CompilationUnit antlrResult = (CompilationUnit) compilationUnit.accept(new ToJavaParseTreeVisitor());

            ASTParser jdkParser = ASTParser.newParser(AST.JLS10);
            jdkParser.setCompilerOptions(options);
            jdkParser.setSource(input.toCharArray());
            jdkParser.setKind(ASTParser.K_COMPILATION_UNIT);
            CompilationUnit jdtResult = (CompilationUnit) jdkParser.createAST(null);

            if (!exclude.contains(path.toString())) {
                assertTrue(antlrResult.subtreeMatch(new CustomASTMatcher(), jdtResult));
            }
        }
    }


}
