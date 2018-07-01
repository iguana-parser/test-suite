package iguana;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.eclipse.jdt.core.JavaCore.COMPILER_SOURCE;

public class Utils {

    private static final String JDK7_DIR = "JDK7_DIR";

    public static String getJDK7SourceLocation() {
        return System.getenv(JDK7_DIR);
    }

    public static String getFileContent(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    public static void writeContentToFile(String content, String path) throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(path)) {
            out.write(content);
        }
    }

    public static List<Path> getFiles(String path, String ext) throws IOException {
        return Files.walk(Paths.get(path))
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(ext))
                .collect(toList());
    }

    public static ASTParser newASTParser(String input) {
        ASTParser astParser = ASTParser.newParser(AST.JLS10);
        astParser.setCompilerOptions(getCompilerOptions());
        astParser.setSource(input.toCharArray());
        astParser.setKind(ASTParser.K_COMPILATION_UNIT);
        return astParser;
    }

    static Map<String, String> getCompilerOptions() {
        Map<String, String> options = new HashMap<>();
        options.put(COMPILER_SOURCE, "1.7");
        return options;
    }

}
