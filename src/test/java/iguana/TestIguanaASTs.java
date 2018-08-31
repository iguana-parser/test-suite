package iguana;

import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.transformation.DesugarPrecedenceAndAssociativity;
import org.iguana.grammar.transformation.DesugarStartSymbol;
import org.iguana.grammar.transformation.EBNFToBNF;
import org.iguana.grammar.transformation.LayoutWeaver;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static iguana.Utils.*;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

class TestIguanaASTs {

    private IguanaParser parser;

    @BeforeEach
    void init() throws Exception {
        Grammar grammar = Grammar.load(new File(this.getClass().getResource("/JavaNat").toURI()));

        grammar = new EBNFToBNF().transform(grammar);

        DesugarPrecedenceAndAssociativity precedence = new DesugarPrecedenceAndAssociativity();
        precedence.setOP2();

        grammar = precedence.transform(grammar);
        grammar = new LayoutWeaver().transform(grammar);
        grammar = new DesugarStartSymbol().transform(grammar);

        parser = new IguanaParser(grammar);
    }

    @TestFactory
    Collection<DynamicTest> testIguana() throws Exception {
        List<Path> javaFiles = getFiles(getSourceDir(), ".java");

        return javaFiles.stream().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
            String inputContent = getFileContent(path);
            Input input = Input.fromString(inputContent);

            ParseTreeNode parseTreeNode = parser.getParserTree(input);
            assertNotNull(parseTreeNode);

            ASTParser astParser = newASTParser(inputContent);
            CompilationUnit eclipseJDTResult = (CompilationUnit) astParser.createAST(null);

            ASTNode iguanaResult = (ASTNode) parseTreeNode.accept(new IguanaToJavaParseTreeVisitor());

            assertTrue(iguanaResult.subtreeMatch(new CustomASTMatcher(), eclipseJDTResult));
        })).collect(toList());
    }


    @Test
    void testAllInOne() throws Exception {
        String inputContent = getFileContent(Paths.get(this.getClass().getResource("/AllInOne7.java").toURI()));
        Input input = Input.fromString(inputContent);

        ParseTreeNode parseTreeNode = parser.getParserTree(input);
        assertNotNull(parseTreeNode);

        ASTNode iguanaResult = (ASTNode) parseTreeNode.accept(new IguanaToJavaParseTreeVisitor());

        ASTParser astParser = newASTParser(inputContent);
        CompilationUnit eclipseJDTResult = (CompilationUnit) astParser.createAST(null);

        assertTrue(iguanaResult.subtreeMatch(new CustomASTMatcher(), eclipseJDTResult));
    }

}
