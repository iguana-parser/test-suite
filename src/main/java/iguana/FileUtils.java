package iguana;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class FileUtils {

    static String getFileContent(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    static void writeContentToFile(String content, String path) throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(path)) {
            out.write(content);
        }
    }
}
