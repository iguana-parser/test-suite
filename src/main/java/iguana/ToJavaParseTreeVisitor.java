package iguana;

import antlr4java.JavaParser;
import antlr4java.JavaParserBaseVisitor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class ToJavaParseTreeVisitor extends JavaParserBaseVisitor<JsonNode> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public JsonNode visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        ArrayNode typeDeclarations = node.putArray("typeDeclarations");
        typeDeclarations.addAll(ctx.typeDeclaration().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));
        return node;
    }

    @Override
    public JsonNode visitClassOrInterfaceTypeDeclaration(JavaParser.ClassOrInterfaceTypeDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        ArrayNode modifiers = node.putArray("modifiers");
        modifiers.addAll(ctx.classOrInterfaceModifier().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));

        JsonNode declNode;
        if (ctx.classDeclaration() != null) {
            declNode = ctx.classDeclaration().accept(this);
        } else if (ctx.enumDeclaration() != null) {
            declNode = ctx.enumDeclaration().accept(this);
        } else if (ctx.interfaceDeclaration() != null) {
            declNode = ctx.interfaceDeclaration().accept(this);
        } else if (ctx.annotationTypeDeclaration() != null){
            declNode = ctx.annotationTypeDeclaration().accept(this);
        } else {
            throw new RuntimeException("Unexpected type declaration");
        }

        node.set("decl", declNode);
        return node;
    }

    @Override
    public JsonNode visitSemicolonTypeDeclaration(JavaParser.SemicolonTypeDeclarationContext ctx) {
        return null;
    }

    @Override
    public JsonNode visitClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx) {
        if (ctx.annotation() != null) {
            return ctx.annotation().accept(this);
        } else {
            return TextNode.valueOf(ctx.getChild(0).getText());
        }
    }

    @Override
    public JsonNode visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        addName(node, ctx.IDENTIFIER());

        if (ctx.typeParameters() != null) {
            ArrayNode typeParameters = node.putArray("typeParameters");
            ctx.typeParameters().accept(this).forEach(typeParameters::add);
        }

        if (ctx.typeType() != null) {
            node.put("extends", ctx.typeType().getText());
        }

        if (ctx.typeList() != null) {
            node.set("implements", ctx.typeList().accept(this));
        }

        node.set("classBody", ctx.classBody().accept(this));

        return node;
    }

    @Override
    public JsonNode visitTypeParameters(JavaParser.TypeParametersContext ctx) {
        return createArrayNode(ctx.typeParameter());
    }

    @Override
    public JsonNode visitTypeParameter(JavaParser.TypeParameterContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.put("name", ctx.IDENTIFIER().getText());

        node.set("annotations", createArrayNode(ctx.annotation()));

        ArrayNode typeBounds = node.putArray("typeBounds");
        if (ctx.typeBound() != null) {
            ctx.typeBound().accept(this).forEach(typeBounds::add);
        }

        return node;
    }

    @Override
    public JsonNode visitTypeList(JavaParser.TypeListContext ctx) {
        return createArrayNode(ctx.typeType());
    }

    @Override
    public JsonNode visitTypeBound(JavaParser.TypeBoundContext ctx) {
        return createArrayNode(ctx.typeType());
    }

    @Override
    public JsonNode visitTypeType(JavaParser.TypeTypeContext ctx) {
        return TextNode.valueOf(ctx.getText());
    }

    @Override
    public JsonNode visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.put("name", ctx.IDENTIFIER().getText());
        return node;
    }

    @Override
    public JsonNode visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.put("name", ctx.IDENTIFIER().getText());
        if (ctx.typeList() != null) {
            node.set("implements", ctx.typeList().accept(this));
        }
        if (ctx.enumConstants() != null) {
            node.set("enumConstants", ctx.enumConstants().accept(this));
        }
        return node;
    }

    @Override
    public JsonNode visitEnumConstants(JavaParser.EnumConstantsContext ctx) {
        return createArrayNode(ctx.enumConstant());
    }

    @Override
    public JsonNode visitEnumConstant(JavaParser.EnumConstantContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        if (!ctx.annotation().isEmpty()) {
            node.set("annotations", createArrayNode(ctx.annotation()));
        }

        node.put("name", ctx.IDENTIFIER().getText());

        if (ctx.arguments() != null) {
            node.set("arguments", ctx.arguments().accept(this));
        }

        if (ctx.classBody() != null) {
            node.set("classBody", ctx.classBody().accept(this));
        }

        return node;
    }

    @Override
    public JsonNode visitArguments(JavaParser.ArgumentsContext ctx) {
        if (ctx.expressionList() == null) {
            return mapper.createArrayNode();
        }
        return ctx.expressionList().accept(this);
    }

    @Override
    public JsonNode visitClassBody(JavaParser.ClassBodyContext ctx) {
        return createArrayNode(ctx.classBodyDeclaration());
    }

    @Override
    public JsonNode visitBlockClassBodyDeclration(JavaParser.BlockClassBodyDeclrationContext ctx) {
        ObjectNode node = createJsonNode("ClassBodyDeclaration");

        if (ctx.STATIC() != null) {
            node.put("static", true);
        } else {
            node.put("static", false);
        }

        node.set("block", ctx.block().accept(this));

        return node;
    }

    @Override
    public JsonNode visitBlock(JavaParser.BlockContext ctx) {
        return createArrayNode(ctx.blockStatement());
    }

    @Override
    public JsonNode visitBlockStatement(JavaParser.BlockStatementContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public JsonNode visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);

        if (!ctx.variableModifier().isEmpty()) {
            node.set("modifiers", createArrayNode(ctx.variableModifier()));
        }

        node.put("type", ctx.typeType().getText());

        node.set("variableDeclarators", ctx.variableDeclarators().accept(this));

        return node;
    }

    @Override
    public JsonNode visitVariableDeclarators(JavaParser.VariableDeclaratorsContext ctx) {
        return createArrayNode(ctx.variableDeclarator());
    }

    @Override
    public JsonNode visitVariableDeclarator(JavaParser.VariableDeclaratorContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.put("id", ctx.variableDeclaratorId().getText());
        if (ctx.variableInitializer() != null) {
            node.set("variableInitializer", ctx.variableInitializer().accept(this));
        }
        return node;
    }

    @Override
    public JsonNode visitVariableInitializer(JavaParser.VariableInitializerContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public JsonNode visitArrayInitializer(JavaParser.ArrayInitializerContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.set("variableInitializers", createArrayNode(ctx.variableInitializer()));
        return node;
    }

    @Override
    public JsonNode visitExpressionList(JavaParser.ExpressionListContext ctx) {
        return createArrayNode(ctx.expression());
    }

    @Override
    public JsonNode visitPrimaryExpr(JavaParser.PrimaryExprContext ctx) {
        return ctx.primary().accept(this);
    }

    @Override
    public JsonNode visitParenthesisExpr(JavaParser.ParenthesisExprContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        node.set("expression", ctx.expression().accept(this));
        return node;
    }

    @Override
    public JsonNode visitLiteralExpr(JavaParser.LiteralExprContext ctx) {
        return TextNode.valueOf(ctx.getText());
    }

    @Override
    public JsonNode visitAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx) {
        ObjectNode node = createJsonNode(ctx);
        addName(node, ctx.IDENTIFIER());
        return node;
    }

    private ObjectNode createJsonNode(ParserRuleContext ctx) {
        return createJsonNode(ctx.getClass().getSimpleName());
    }

    private ObjectNode createJsonNode(String name) {
        ObjectNode node = mapper.createObjectNode();
        node.put("kind", name.substring(0, name.length() - "context".length()));
        return node;
    }

    private <T extends ParserRuleContext> ArrayNode createArrayNode(List<T> list) {
        ArrayNode annotations = mapper.createArrayNode();
        annotations.addAll(list.stream().map(annotation -> annotation.accept(this)).filter(Objects::nonNull).collect(toList()));
        return annotations;
    }

    private void addName(ObjectNode node, TerminalNode identifier) {
        node.put("name", identifier.getText());
    }
}
