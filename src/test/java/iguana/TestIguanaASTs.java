package iguana;

import iguana.utils.input.Input;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.GrammarGraph;
import org.iguana.grammar.symbol.Nonterminal;
import org.iguana.grammar.transformation.DesugarPrecedenceAndAssociativity;
import org.iguana.grammar.transformation.DesugarStartSymbol;
import org.iguana.grammar.transformation.EBNFToBNF;
import org.iguana.grammar.transformation.LayoutWeaver;
import org.iguana.parser.Iguana;
import org.iguana.parser.ParseResult;
import org.iguana.parser.ParserRuntime;
import org.iguana.sppf.NonPackedNode;
import org.iguana.util.Configuration;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static iguana.Utils.*;
import static java.util.stream.Collectors.toList;

class TestIguanaASTs {

    private GrammarGraph grammarGraph;
    private Grammar grammar;
    private Nonterminal start;

    @BeforeEach
    void init() throws Exception {
        grammar = Grammar.load(new File(this.getClass().getResource("/JavaNat").toURI()));

        grammar = new EBNFToBNF().transform(grammar);

        DesugarPrecedenceAndAssociativity precedence = new DesugarPrecedenceAndAssociativity();
        precedence.setOP2();

        grammar = precedence.transform(grammar);
        grammar = new LayoutWeaver().transform(grammar);
        grammar = new DesugarStartSymbol().transform(grammar);

        start = Nonterminal.withName(grammar.getStartSymbol().getName());
        grammarGraph = GrammarGraph.from(grammar);
    }

    @TestFactory
    Collection<DynamicTest> testIguana() throws Exception {
        List<Path> javaFiles = getFiles(getJDK7SourceLocation(), ".java");

        return javaFiles.stream().map(path -> DynamicTest.dynamicTest(path.toString(), () -> {
            ParseResult<NonPackedNode> result = Iguana.run(Input.fromString(getFileContent(path)), new ParserRuntime(Configuration.load()), grammarGraph, start, Collections.emptyMap(), true);
            Assert.assertTrue(result.isParseSuccess());
        })).collect(toList());
    }


    @Test
    void test() throws Exception {
        String inputContent = getFileContent(Paths.get(this.getClass().getResource("/AllInOne7.java").toURI()));
        Input input = Input.fromString(inputContent);
        ParseResult<NonPackedNode> result = Iguana.parse(input, grammar);
        if (result.isParseError()) {
            System.out.println(input.getLineNumber(result.asParseError().getInputIndex()) + ":" + input.getColumnNumber(result.asParseError().getInputIndex()));
        }
        System.out.println(result);
    }

}