package iguana.benchmark;

import iguana.utils.input.Input;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;
import org.openjdk.jmh.annotations.*;

import java.nio.file.Paths;

import static iguana.Utils.getFileContent;
import static iguana.Utils.getJavaGrammar;

@State(Scope.Benchmark)
public class IguanaBenchmark {

    @Param({""})
    private String path;

    private Input input;

    private IguanaParser parser;

    @Setup
    public void init() throws Exception {
        parser = new IguanaParser(getJavaGrammar());
        input = Input.fromString(getFileContent(Paths.get(path)));
    }

    @Benchmark
    public ParseTreeNode benchmarkParse() {
        return parser.getParserTree(input);
    }

}
