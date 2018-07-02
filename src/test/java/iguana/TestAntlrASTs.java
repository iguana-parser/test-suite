package iguana;

import antlr4java.JavaParser;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import static iguana.Utils.*;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertTrue;

class TestAntlrASTs {

    @TestFactory
    Collection<DynamicTest> testAntlr() throws Exception {
        List<Path> javaFiles = getFiles(getJDK7SourceLocation(), ".java");

        return javaFiles.stream().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
            String input = getFileContent(path);
            AntlrJavaParser parser = new AntlrJavaParser();
            JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
            CompilationUnit antlrResult = (CompilationUnit) compilationUnit.accept(new AntlrToJavaParseTreeVisitor());

            ASTParser astParser = newASTParser(input);
            CompilationUnit eclipseJDTResult = (CompilationUnit) astParser.createAST(null);

            assertTrue(antlrResult.subtreeMatch(new CustomASTMatcher(), eclipseJDTResult));
        })).collect(toList());
    }
}
