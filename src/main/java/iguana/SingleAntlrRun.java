package iguana;

import antlr4java.JavaParser;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static iguana.Utils.*;

public class SingleAntlrRun {

    public static void main(String[] args) throws IOException {
        String projectName = args[0];

        if (projectName == null) {
            throw new RuntimeException("Please provide a valid project name in the source folder");
        }

        AntlrJavaParser antlrParser = new AntlrJavaParser();

        List<Path> files = getFiles(getSourceDir() + "/" + projectName, ".java");

        int count = 0;

        for (Path path : files) {
            String input = getFileContent(Paths.get(path.toString()));
            JavaParser.CompilationUnitContext result = antlrParser.parse(input);
            if (result != null) {
                count++;
            }
        }

        System.out.println(count);
    }
}
