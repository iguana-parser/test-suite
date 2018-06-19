package iguana;

import antlr4java.JavaLexer;
import antlr4java.JavaParser;
import antlr4java.JavaParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

public class ToJavaParseTreeVisitor extends JavaParserBaseVisitor<ASTNode> {

    private AST ast = AST.newAST(AST.JLS10);

    @Override
    public ASTNode visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        CompilationUnit compilationUnit = ast.newCompilationUnit();
        compilationUnit.types().addAll(ctx.typeDeclaration().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));
        return compilationUnit;
    }

    @Override
    public ASTNode visitClassOrInterfaceTypeDeclaration(JavaParser.ClassOrInterfaceTypeDeclarationContext ctx) {
        ASTNode node;

        if (ctx.classDeclaration() != null) {
            node = ctx.classDeclaration().accept(this);
        } else if (ctx.interfaceDeclaration() != null) {
            node = ctx.interfaceDeclaration().accept(this);
        } else if (ctx.enumDeclaration() != null) {
            node = ctx.enumDeclaration().accept(this);
        } else if (ctx.annotationTypeDeclaration() != null){
            node = ctx.annotationTypeDeclaration().accept(this);
        } else {
            throw new RuntimeException("Unexpected type declaration");
        }

        ((BodyDeclaration) node).modifiers().addAll(ctx.classOrInterfaceModifier().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));
        return node;
    }

    @Override
    public ASTNode visitClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx) {
        if (ctx.annotation() != null) {
            return ctx.annotation().accept(this);
        }

        return ast.newModifier(Modifier.ModifierKeyword.toKeyword(ctx.getChild(0).getText()));
    }

    @Override
    public ASTNode visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
        TypeDeclaration classDeclaration = ast.newTypeDeclaration();
        classDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));

        if (ctx.typeParameters() != null) {
            classDeclaration.typeParameters().addAll(createList(ctx.typeParameters().typeParameter()));
        }

        if (ctx.typeType() != null) {
            classDeclaration.setSuperclassType((Type) ctx.typeType().accept(this));
        }

        if (ctx.typeList() != null) {
            classDeclaration.superInterfaceTypes().addAll(createList(ctx.typeList().typeType()));
        }

        classDeclaration.bodyDeclarations().addAll(createList(ctx.classBody().classBodyDeclaration()));
        return classDeclaration;
    }

    @Override
    public ASTNode visitTypeParameter(JavaParser.TypeParameterContext ctx) {
        TypeParameter typeParameter = ast.newTypeParameter();
        typeParameter.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));

        typeParameter.modifiers().add(createList(ctx.annotation()));

        typeParameter.typeBounds().add(createList(ctx.typeBound().typeType()));

        return typeParameter;
    }

    @Override
    public ASTNode visitTypeType(JavaParser.TypeTypeContext ctx) {
        if (ctx.classOrInterfaceType() != null) {
            return ctx.classOrInterfaceType().accept(this);
        }
        return ast.newPrimitiveType(PrimitiveType.toCode(ctx.primitiveType().getText()));
    }

    @Override
    public ASTNode visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
        int i = 0;
        Type type = ast.newSimpleType(ast.newSimpleName(ctx.IDENTIFIER().get(i++).getText()));

        if (ctx.getChildCount() > 1 && ctx.getChild(i) instanceof JavaParser.TypeArgumentsContext) {
            ParameterizedType parameterizedType = ast.newParameterizedType(type);
            parameterizedType.typeArguments().add(ctx.getChild(i++).accept(this));
            type = parameterizedType;
        }

        while (true) {
            if (i >= ctx.getChildCount()) break;
            if (isIdentifier(ctx.getChild(i))) {
                type = ast.newQualifiedType(type, ast.newSimpleName(ctx.getChild(i).getText()));
                if (i + 1 < ctx.getChildCount() && ctx.getChild(i + 1) instanceof JavaParser.TypeArgumentsContext) {
                    type = ast.newParameterizedType(type);
                    ((ParameterizedType) type).typeArguments().add(ctx.getChild(i + 1).accept(this));
                    i++;
                }
                i++;
            } else {
                break;
            }
        }

        return type;
    }

    @Override
    public ASTNode visitTypeArgument(JavaParser.TypeArgumentContext ctx) {
        TypeParameter typeParameter = ast.newTypeParameter();
        return typeParameter;
    }

    @Override
    public ASTNode visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        TypeDeclaration interfaceDeclaration = ast.newTypeDeclaration();
        interfaceDeclaration.setInterface(true);
        interfaceDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        return interfaceDeclaration;
    }

    @Override
    public ASTNode visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
        enumDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        if (ctx.typeList() != null) {
            enumDeclaration.superInterfaceTypes().addAll(createList(ctx.typeList().typeType()));
        }
        enumDeclaration.enumConstants().addAll(createList(ctx.enumConstants().enumConstant()));
        return enumDeclaration;
    }

    @Override
    public EnumConstantDeclaration visitEnumConstant(JavaParser.EnumConstantContext ctx) {
        EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

        enumConstantDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));

        enumConstantDeclaration.modifiers().addAll(createList(ctx.annotation()));

        if (ctx.arguments() != null) {
            enumConstantDeclaration.arguments().add(createList(ctx.arguments().expressionList().expression()));
        }

        return enumConstantDeclaration;
    }

    @Override
    public ASTNode visitBlockClassBodyDeclration(JavaParser.BlockClassBodyDeclrationContext ctx) {
        Block block = ast.newBlock();
        block.statements().addAll(createList(ctx.block().blockStatement()));
        return block;
    }

    @Override
    public ASTNode visitBlockStatement(JavaParser.BlockStatementContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ASTNode visitLocalVariableDeclaration(JavaParser.LocalVariableDeclarationContext ctx) {
        VariableDeclarationExpression variableDeclaration = (VariableDeclarationExpression) ctx.variableDeclarators().accept(this);
        variableDeclaration.setType((Type) ctx.typeType().accept(this));
        variableDeclaration.modifiers().addAll(createList(ctx.variableModifier()));
        return variableDeclaration;
    }

    @Override
    public VariableDeclarationExpression visitVariableDeclarators(JavaParser.VariableDeclaratorsContext ctx) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        fragment.setName(ast.newSimpleName(ctx.variableDeclarator(0).variableDeclaratorId().IDENTIFIER().getText()));
        fragment.setInitializer((Expression) ctx.variableDeclarator(0).accept(this));
        VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragment);

        // TODO: add other fragments
        return variableDeclarationExpression;
    }

    @Override
    public ASTNode visitVariableInitializer(JavaParser.VariableInitializerContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ASTNode visitArrayInitializer(JavaParser.ArrayInitializerContext ctx) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll(createList(ctx.variableInitializer()));
        return arrayInitializer;
    }

    @Override
    public ASTNode visitPrimaryExpr(JavaParser.PrimaryExprContext ctx) {
        return ctx.primary().accept(this);
    }

    @Override
    public ASTNode visitPrimary(JavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            return ctx.expression().accept(this);
        } else if (ctx.THIS() != null) {
            return ast.newThisExpression();
        } else if (ctx.SUPER() != null) {
            return ast.newSuperFieldAccess();
        } else if (ctx.literal() != null) {
            return ctx.literal().accept(this);
        } else if (ctx.IDENTIFIER() != null) {
            return ast.newSimpleName(ctx.IDENTIFIER().getText());
        } else if (ctx.typeTypeOrVoid() != null) {
            TypeLiteral typeLiteral = ast.newTypeLiteral();
            typeLiteral.setType((Type) ctx.typeTypeOrVoid().accept(this));
            return typeLiteral;
        } else {
            throw new RuntimeException();
        }
    }

    @Override
    public ASTNode visitTypeTypeOrVoid(JavaParser.TypeTypeOrVoidContext ctx) {
        if (ctx.VOID() != null) {
            return ast.newPrimitiveType(PrimitiveType.VOID);
        } else {
            return ctx.typeType().accept(this);
        }
    }

    public ASTNode visitAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx) {
        AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
        annotationTypeDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        return annotationTypeDeclaration;
    }

    private <T extends ParserRuleContext> List createList(List<T> list) {
        return list.stream().map(annotation -> annotation.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private boolean isIdentifier(ParseTree node) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == JavaLexer.IDENTIFIER;
    }
}
