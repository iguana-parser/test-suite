package iguana;

import antlr4java.JavaParser;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.internal.formatter.DefaultCodeFormatter;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static iguana.FileUtils.getFileContent;
import static iguana.FileUtils.writeContentToFile;
import static org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE;

public class TestAntlrParseTreeSerializer {

    private AntlrJavaParser parser = new AntlrJavaParser();
    private Map<String, String> options = new HashMap<>();

    @BeforeEach
    void init() {
        options.put(COMPILER_SOURCE, "1.7");
    }

    @Test
    public void test() throws Exception {
        URI uri = TestAntlrParseTreeSerializer.class.getClassLoader().getResource("AllInOne7.java").toURI();
        String input = getFileContent(Paths.get(uri));
        JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
        CompilationUnit antlrResult = (CompilationUnit) compilationUnit.accept(new ToJavaParseTreeVisitor());
        writeContentToFile(antlrResult.toString(), "/Users/Ali/Desktop/ast_antlr.txt");


        ASTParser jdkParser = ASTParser.newParser(AST.JLS10);
        jdkParser.setCompilerOptions(options);
        jdkParser.setSource(input.toCharArray());
        jdkParser.setKind(ASTParser.K_COMPILATION_UNIT);
        CompilationUnit jdtResult = (CompilationUnit) jdkParser.createAST(null);
        writeContentToFile(jdtResult.toString(), "/Users/Ali/Desktop/ast_jdt.txt");

        System.out.println(antlrResult.subtreeMatch(new ASTMatcher(), jdtResult));
    }

}
