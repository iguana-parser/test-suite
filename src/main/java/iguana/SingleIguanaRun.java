package iguana;

import iguana.utils.input.Input;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static iguana.Utils.*;

public class SingleIguanaRun {

    public static void main(String[] args) throws IOException {
        String projectName = args[0];

        if (projectName == null) {
            throw new RuntimeException("Please provide a valid project name in the source folder");
        }

        IguanaParser iguanaParser = new IguanaParser(getJavaGrammar());

        List<Path> files = getFiles(getSourceDir() + "/" + projectName, ".java");

        int count = 0;

        for (Path path : files) {
            String input = getFileContent(Paths.get(path.toString()));
            ParseTreeNode parserTree = iguanaParser.getParserTree(Input.fromString(input));
            if (parserTree != null) {
                count++;
            }
        }

        System.out.println(count);
    }
}
