package iguana;

import antlr4java.JavaParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AntlrJavaParseTreeSerializer {

    private static AntlrJavaParser parser = new AntlrJavaParser();

    public static void main(String[] args) throws JsonProcessingException {
        JavaParser.CompilationUnitContext compilationUnit = parser.parse("class Test { }");
        JsonNode result = compilationUnit.accept(new ToJavaParseTreeVisitor());
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        System.out.println(result);
    }
}
