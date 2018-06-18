package iguana;

import antlr4java.JavaParser;
import antlr4java.JavaParserBaseVisitor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class ToJavaParseTreeVisitor extends JavaParserBaseVisitor<JsonNode> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public JsonNode visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        ObjectNode node = mapper.createObjectNode();
        node.put("kind", "CompilationUnit");
        ArrayNode typeDeclarations = node.putArray("typeDeclarations");
        typeDeclarations.addAll(ctx.typeDeclaration().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));
        return node;
    }

    @Override
    public JsonNode visitTypeDeclaration(JavaParser.TypeDeclarationContext ctx) {
        ObjectNode node = mapper.createObjectNode();
        node.put("kind", "TypeDeclaration");
        ArrayNode modifiers = node.putArray("modifiers");
        modifiers.addAll(ctx.classOrInterfaceModifier().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));


        JsonNode declNode;
        if (ctx.classDeclaration() != null) {
            declNode = ctx.classDeclaration().accept(this);
        } else if (ctx.enumDeclaration() != null) {
            declNode = ctx.enumDeclaration().accept(this);
        } else if (ctx.interfaceDeclaration() != null) {
            declNode = ctx.interfaceDeclaration().accept(this);
        } else {
            declNode = ctx.annotationTypeDeclaration().accept(this);
        }

        node.set("decl", declNode);
        return node;
    }

    @Override
    public JsonNode visitClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx) {
        return super.visitClassOrInterfaceModifier(ctx);
    }

    @Override
    public JsonNode visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        ObjectNode node = mapper.createObjectNode();
        node.put("kind", "ClassDeclaration");
        return node;
    }

    @Override
    public JsonNode visitTerminal(TerminalNode node) {
        return TextNode.valueOf(node.getText());
    }
}
