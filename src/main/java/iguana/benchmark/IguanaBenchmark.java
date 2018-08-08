package iguana.benchmark;

import iguana.IguanaToJavaParseTreeVisitor;
import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.iguana.grammar.Grammar;
import org.iguana.grammar.transformation.DesugarPrecedenceAndAssociativity;
import org.iguana.grammar.transformation.DesugarStartSymbol;
import org.iguana.grammar.transformation.EBNFToBNF;
import org.iguana.grammar.transformation.LayoutWeaver;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Paths;

import static iguana.Utils.getFileContent;

@State(Scope.Benchmark)
public class IguanaBenchmark {

    @Param({""})
    private String path;

    String inputContent;

    private Input input;

    private IguanaParser parser;

    @Setup
    public void init() throws Exception {
        Grammar grammar = Grammar.load(this.getClass().getResourceAsStream("/JavaNat"));

        grammar = new EBNFToBNF().transform(grammar);

        DesugarPrecedenceAndAssociativity precedence = new DesugarPrecedenceAndAssociativity();
        precedence.setOP2();

        grammar = precedence.transform(grammar);
        grammar = new LayoutWeaver().transform(grammar);
        grammar = new DesugarStartSymbol().transform(grammar);

        parser = new IguanaParser(grammar);

        inputContent = getFileContent(Paths.get(path));
        input = Input.fromString(getFileContent(Paths.get(path)));
    }

    @Benchmark
    public ParseTreeNode benchmarkParse() {
        return parser.getParserTree(input);
    }

    @Benchmark
    public CompilationUnit benchmarkAST() {
        ParseTreeNode parserTree = parser.getParserTree(input);
        return (CompilationUnit) parserTree.accept(new IguanaToJavaParseTreeVisitor(input));
    }

}
