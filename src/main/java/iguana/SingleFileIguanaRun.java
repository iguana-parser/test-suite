package iguana;

import iguana.utils.input.Input;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static iguana.Utils.*;

public class SingleFileIguanaRun {

    public static void main(String[] args) throws IOException {
        String fileName = args[0];

        IguanaParser iguanaParser = new IguanaParser(getJavaGrammar());

        String input = getFileContent(Paths.get(fileName));
        ParseTreeNode parserTree = iguanaParser.getParserTree(Input.fromString(input));
        if (parserTree != null) {
            System.out.print(fileName + "," + input.length() + "," + parserTree.getName() + ",");
        }
    }
}
