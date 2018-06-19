package iguana;

import antlr4java.JavaParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Paths;

import static iguana.FileUtils.getFileContent;

public class TestAntlrParseTreeSerializer {

    private AntlrJavaParser parser = new AntlrJavaParser();

    @Test
    public void test() throws Exception {
        URI uri = TestAntlrParseTreeSerializer.class.getClassLoader().getResource("AllInOne7.java").toURI();
        String input = getFileContent(Paths.get(uri));
        JavaParser.CompilationUnitContext compilationUnit = parser.parse(input);
        JsonNode result = compilationUnit.accept(new ToJavaParseTreeVisitor());
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(result);
    }
}
