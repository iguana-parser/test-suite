package iguana;

import antlr4java.JavaParser;
import iguana.utils.input.Input;
import org.iguana.parser.IguanaParser;
import org.iguana.parsetree.ParseTreeNode;

import java.io.IOException;
import java.nio.file.Paths;

import static iguana.Utils.getFileContent;
import static iguana.Utils.getJavaGrammar;

public class SingleFileAntlrRun {

    public static void main(String[] args) throws IOException {
        String fileName = args[0];

        AntlrJavaParser antlrParser = new AntlrJavaParser();

        String input = getFileContent(Paths.get(fileName));
        JavaParser.CompilationUnitContext result = antlrParser.parse(input);
        if (result != null) {
            System.out.print(fileName + "," + input.length() + "," + result.getRuleContext() + ",");
        }
    }
}
