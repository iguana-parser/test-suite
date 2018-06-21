package iguana;

import antlr4java.JavaLexer;
import antlr4java.JavaParser;
import antlr4java.JavaParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.Collections;
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
    public Annotation visitAnnotation(JavaParser.AnnotationContext ctx) {
        Annotation annotation;

        if (ctx.elementValue() == null && ctx.elementValuePairs() == null) {             // MarkerAnnotation
            annotation = ast.newMarkerAnnotation();
        } else if (ctx.elementValuePairs() == null) { // SingleValueAnnoation
            annotation = ast.newSingleMemberAnnotation();
            ((SingleMemberAnnotation) annotation).setValue((Expression) ctx.elementValue().accept(this));
        } else {
            annotation = ast.newNormalAnnotation();
            ((NormalAnnotation) annotation).values().addAll(createList(ctx.elementValuePairs().elementValuePair()));
        }

        annotation.setTypeName((Name) ctx.qualifiedName().accept(this));
        return annotation;
    }

    @Override
    public ASTNode visitElementValue(JavaParser.ElementValueContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ASTNode visitElementValuePair(JavaParser.ElementValuePairContext ctx) {
        MemberValuePair memberValuePair = ast.newMemberValuePair();
        memberValuePair.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        memberValuePair.setValue((Expression) ctx.elementValue().accept(this));
        return memberValuePair;
    }

    @Override
    public ASTNode visitVariableModifier(JavaParser.VariableModifierContext ctx) {
        if (ctx.annotation() != null) {
            return ctx.annotation().accept(this);
        }
        return ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD);
    }

    @Override
    public TypeDeclaration visitClassDeclaration(JavaParser.ClassDeclarationContext ctx) {
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

        typeParameter.modifiers().addAll(createList(ctx.annotation()));

        if (ctx.typeBound() != null) {
            typeParameter.typeBounds().addAll(createList(ctx.typeBound().typeType()));
        }

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
            parameterizedType.typeArguments().addAll(createList(((JavaParser.TypeArgumentsContext) ctx.getChild(i++)).typeArgument()));
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
    public ASTNode visitSimpleTypeArgument(JavaParser.SimpleTypeArgumentContext ctx) {
        return ctx.typeType().accept(this);
    }

    @Override
    public ASTNode visitWildCardTypeArgument(JavaParser.WildCardTypeArgumentContext ctx) {
        WildcardType wildcardType = ast.newWildcardType();
        if (ctx.typeType() != null) {
            wildcardType.setBound((Type) ctx.typeType().accept(this));
        }
        return wildcardType;
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
            enumConstantDeclaration.arguments().addAll(createList(ctx.arguments().expressionList().expression()));
        }

        return enumConstantDeclaration;
    }

    @Override
    public ASTNode visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
        if (ctx.block() != null) {
            Initializer initializer = ast.newInitializer();
            Block body = (Block) ctx.block().accept(this);
            if (body != null) {
                initializer.setBody(body);
            }
            return initializer;
        }
        else if (ctx.memberDeclaration() != null) {
            BodyDeclaration bodyDeclaration = (BodyDeclaration) ctx.memberDeclaration().accept(this);
            if (bodyDeclaration != null) { // TODO: Remove this null when all the cases for body declaration are added
                bodyDeclaration.modifiers().addAll(createList(ctx.modifier()));
            }
            return bodyDeclaration;
        } else {
            return null;
        }
    }

    @Override
    public ASTNode visitMemberDeclaration(JavaParser.MemberDeclarationContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ASTNode visitBlock(JavaParser.BlockContext ctx) {
        Block block = ast.newBlock();
        block.statements().addAll(createList(ctx.blockStatement()));
        return block;
    }

    @Override
    public ASTNode visitMethodDeclaration(JavaParser.MethodDeclarationContext ctx) {
        MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
        methodDeclaration.setReturnType2((Type) ctx.typeTypeOrVoid().accept(this));
        methodDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        methodDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        if (ctx.qualifiedNameList() != null) {
            methodDeclaration.thrownExceptionTypes().addAll(createList(ctx.qualifiedNameList().qualifiedName()).stream().map(name -> convertNameToType((Name) name)).collect(toList()));
        }
        return methodDeclaration;
    }

    @Override
    public ASTNode visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
        constructorDeclaration.setConstructor(true);
        constructorDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        constructorDeclaration.setName(ast.newSimpleName(ctx.IDENTIFIER().getText()));
        if (ctx.qualifiedNameList() != null) {
            constructorDeclaration.thrownExceptionTypes().addAll(createList(ctx.qualifiedNameList().qualifiedName()));
        }
        return constructorDeclaration;
    }

    @Override
    public ASTNode visitQualifiedName(JavaParser.QualifiedNameContext ctx) {
        List<TerminalNode> identifiers = ctx.IDENTIFIER();
        SimpleName simpleName = ast.newSimpleName(identifiers.get(0).getText());
        if (identifiers.size() == 1) {
            return simpleName;
        }

        Name qualifier = simpleName;
        simpleName = ast.newSimpleName(identifiers.get(1).getText());
        qualifier = ast.newQualifiedName(qualifier, simpleName);

        for (int i = 2; i < identifiers.size(); i++) {
            simpleName = ast.newSimpleName(identifiers.get(i).getText());
            qualifier = ast.newQualifiedName(qualifier, simpleName);
        }

        return qualifier;
    }

    @Override
    public ASTNode visitFormalParameter(JavaParser.FormalParameterContext ctx) {
        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.setType((Type) ctx.typeType().accept(this));
        singleVariableDeclaration.modifiers().addAll(createList(ctx.variableModifier()));
        singleVariableDeclaration.setName(ast.newSimpleName(ctx.variableDeclaratorId().IDENTIFIER().getText()));
        return super.visitFormalParameter(ctx);
    }

    @Override
    public ASTNode visitBlockStatement(JavaParser.BlockStatementContext ctx) {
        if (ctx.localVariableDeclaration() != null) {
            List<VariableDeclarationFragment> fragments = getVariableDeclarationFragment(ctx.localVariableDeclaration());
            VariableDeclarationStatement variableDeclarationStatement = ast.newVariableDeclarationStatement(fragments.get(0));
            variableDeclarationStatement.setType((Type) ctx.localVariableDeclaration().typeType().accept(this));
            variableDeclarationStatement.modifiers().addAll(createList(ctx.localVariableDeclaration().variableModifier()));
            return variableDeclarationStatement;
        } else if (ctx.statement() != null) {
            return ctx.statement().accept(this);
        } else {
            return ctx.localTypeDeclaration().accept(this);
        }
    }

    private List<VariableDeclarationFragment> getVariableDeclarationFragment(JavaParser.LocalVariableDeclarationContext ctx) {
        List<VariableDeclarationFragment> fragments = new ArrayList<>();

        for (JavaParser.VariableDeclaratorContext variableDeclaratorContext : ctx.variableDeclarators().variableDeclarator()) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(ast.newSimpleName(variableDeclaratorContext.variableDeclaratorId().IDENTIFIER().getText()));
            if (variableDeclaratorContext.variableInitializer() != null) {
                fragment.setInitializer((Expression) variableDeclaratorContext.variableInitializer().accept(this));
            }
            fragments.add(fragment);
        }

        return fragments;
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
            return ctx.typeTypeOrVoid().accept(this);
        } else {
            throw new RuntimeException();
        }
    }

    @Override
    public ASTNode visitMethodCallExpr(JavaParser.MethodCallExprContext ctx) {
        MethodInvocation methodInvocation = ast.newMethodInvocation();
        methodInvocation.setName(ast.newSimpleName(ctx.methodCall().IDENTIFIER().getText()));
        return methodInvocation;
    }

    @Override
    public ASTNode visitLiteral(JavaParser.LiteralContext ctx) {
        if (ctx.BOOL_LITERAL() != null) {
            return ast.newBooleanLiteral(Boolean.parseBoolean(ctx.BOOL_LITERAL().getText()));
        } else if (ctx.CHAR_LITERAL() != null) {
            CharacterLiteral characterLiteral = ast.newCharacterLiteral();
            characterLiteral.setCharValue(ctx.CHAR_LITERAL().getText().charAt(0));
            return characterLiteral;
        } else if (ctx.NULL_LITERAL() != null) {
            return ast.newNullLiteral();
        } else if (ctx.STRING_LITERAL() != null) {
            StringLiteral stringLiteral = ast.newStringLiteral();
            String text = ctx.STRING_LITERAL().getText();
            stringLiteral.setLiteralValue(text.substring(1, text.length() - 1));
            return stringLiteral;
        } else  { // integer or float literal
            return ast.newNumberLiteral(ctx.integerLiteral().getText());
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

    private List<SingleVariableDeclaration> getFormalParameters(JavaParser.FormalParametersContext ctx) {
        if (ctx.formalParameterList() == null) {
            return Collections.emptyList();
        }

        final List<SingleVariableDeclaration> list = new ArrayList<>();
        if (ctx.formalParameterList().getChild(0) instanceof JavaParser.FormalParameterContext) {
            createList(ctx.formalParameterList().formalParameter()).forEach(formalParameter -> list.add((SingleVariableDeclaration) formalParameter));
            if (ctx.formalParameterList().lastFormalParameter() != null) {
                SingleVariableDeclaration formalParameter = (SingleVariableDeclaration) ctx.formalParameterList().lastFormalParameter().accept(this);
                if (formalParameter != null) { // TODO: Remove it later
                    list.add(formalParameter);
                }
            }
        } else {
            list.add((SingleVariableDeclaration) ctx.formalParameterList().lastFormalParameter().accept(this));
        }

        return list;
    }

    @Override
    public ASTNode visitLastFormalParameter(JavaParser.LastFormalParameterContext ctx) {
        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.setType((Type) ctx.typeType().accept(this));
        singleVariableDeclaration.modifiers().addAll(createList(ctx.variableModifier()));
        singleVariableDeclaration.setVarargs(true);
        singleVariableDeclaration.setName(ast.newSimpleName(ctx.variableDeclaratorId().IDENTIFIER().getText()));
        return super.visitLastFormalParameter(ctx);
    }

    private <T extends ParserRuleContext> List<ASTNode> createList(List<T> list) {
        return list.stream().map(annotation -> annotation.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private boolean isIdentifier(ParseTree node) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == JavaLexer.IDENTIFIER;
    }

    private Type convertNameToType(Name name) {
        if (name.isSimpleName()) {
            return ast.newSimpleType(name);
        } else {
            QualifiedName qualifiedName = (QualifiedName) name;
            return ast.newQualifiedType(convertNameToType(qualifiedName.getQualifier()), qualifiedName.getName());
        }
    }
}
