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
        memberValuePair.setName(getIdentifier(ctx.IDENTIFIER()));
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
        classDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));

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
        typeParameter.setName(getIdentifier(ctx.IDENTIFIER()));

        typeParameter.modifiers().addAll(createList(ctx.annotation()));

        if (ctx.typeBound() != null) {
            typeParameter.typeBounds().addAll(createList(ctx.typeBound().typeType()));
        }

        return typeParameter;
    }

    @Override
    public Type visitTypeType(JavaParser.TypeTypeContext ctx) {
        Type type;
        if (ctx.classOrInterfaceType() != null) {
            type = (Type) ctx.classOrInterfaceType().accept(this);
        } else {
            type = ast.newPrimitiveType(PrimitiveType.toCode(ctx.primitiveType().getText()));
        }

        if (ctx.dimensions().children != null) { // Array type
            type = ast.newArrayType(type, ctx.dimensions().children.size() / 2);
        }

        return type;
    }

    @Override
    public Type visitClassOrInterfaceType(JavaParser.ClassOrInterfaceTypeContext ctx) {
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
        interfaceDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        return interfaceDeclaration;
    }

    @Override
    public ASTNode visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
        enumDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        if (ctx.typeList() != null) {
            enumDeclaration.superInterfaceTypes().addAll(createList(ctx.typeList().typeType()));
        }
        enumDeclaration.enumConstants().addAll(createList(ctx.enumConstants().enumConstant()));
        return enumDeclaration;
    }

    @Override
    public EnumConstantDeclaration visitEnumConstant(JavaParser.EnumConstantContext ctx) {
        EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

        enumConstantDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));

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
        methodDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        methodDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        if (ctx.qualifiedNameList() != null) {
            methodDeclaration.thrownExceptionTypes().addAll(createList(ctx.qualifiedNameList().qualifiedName()).stream().map(name -> convertNameToType((Name) name)).collect(toList()));
        }

        if (ctx.methodBody() != null) {
            methodDeclaration.setBody((Block) ctx.methodBody().accept(this));
        }
        return methodDeclaration;
    }

    @Override
    public ASTNode visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
        constructorDeclaration.setConstructor(true);
        constructorDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        constructorDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
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
        return singleVariableDeclaration;
    }

    @Override
    public ASTNode visitBlockStatement(JavaParser.BlockStatementContext ctx) {
        if (ctx.localVariableDeclaration() != null) {
            List<VariableDeclarationFragment> fragments = getVariableDeclarationFragment(ctx.localVariableDeclaration());
            VariableDeclarationStatement variableDeclarationStatement = ast.newVariableDeclarationStatement(fragments.get(0));
            for (int i = 1; i < fragments.size(); i++) {
                variableDeclarationStatement.fragments().add(fragments.get(i));
            }
            variableDeclarationStatement.setType((Type) ctx.localVariableDeclaration().typeType().accept(this));
            variableDeclarationStatement.modifiers().addAll(createList(ctx.localVariableDeclaration().variableModifier()));
            return variableDeclarationStatement;
        } else if (ctx.statement() != null) {
            return ctx.statement().accept(this);
        } else {
            return ctx.localTypeDeclaration().accept(this);
        }
    }

    @Override
    public TypeDeclarationStatement visitLocalTypeDeclaration(JavaParser.LocalTypeDeclarationContext ctx) {
        TypeDeclaration typeDeclaration;
        if (ctx.classDeclaration() != null) {
            typeDeclaration = (TypeDeclaration) ctx.classDeclaration().accept(this);
        } else {
            typeDeclaration = (TypeDeclaration) ctx.interfaceDeclaration().accept(this);
        }
        typeDeclaration.modifiers().addAll(createList(ctx.classOrInterfaceModifier()));
        return ast.newTypeDeclarationStatement(typeDeclaration);
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

        for (int i = 1; i < ctx.variableDeclarator().size(); i++) {
            fragment = ast.newVariableDeclarationFragment();
            fragment.setName(ast.newSimpleName(ctx.variableDeclarator(i).variableDeclaratorId().IDENTIFIER().getText()));
            fragment.setInitializer((Expression) ctx.variableDeclarator(i).accept(this));
            variableDeclarationExpression.fragments().add(fragment);
        }

        return variableDeclarationExpression;
    }

    @Override
    public ASTNode visitVariableInitializer(JavaParser.VariableInitializerContext ctx) {
        return ctx.getChild(0).accept(this);
    }

    @Override
    public ArrayInitializer visitArrayInitializer(JavaParser.ArrayInitializerContext ctx) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll(createList(ctx.variableInitializer()));
        return arrayInitializer;
    }

    @Override
    public ASTNode visitPrimaryExpr(JavaParser.PrimaryExprContext ctx) {
        return ctx.primary().accept(this);
    }

    @Override
    public Expression visitPrimary(JavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ctx.expression().accept(this));
            return parenthesizedExpression;
        }
        else if (ctx.THIS() != null) {
            return ast.newThisExpression();
        }
        else if (ctx.SUPER() != null) {
            return ast.newSuperFieldAccess();
        }
        else if (ctx.literal() != null) {
            return (Expression) ctx.literal().accept(this);
        }
        else if (ctx.IDENTIFIER() != null) {
            return getIdentifier(ctx.IDENTIFIER());
        }
        else if (ctx.typeTypeOrVoid() != null) {
            TypeLiteral typeLiteral = ast.newTypeLiteral();
            typeLiteral.setType((Type) ctx.typeTypeOrVoid().accept(this));
            return typeLiteral;
        }
        else {
            throw new RuntimeException();
        }
    }

    @Override
    public ArrayAccess visitArrayAccessExpr(JavaParser.ArrayAccessExprContext ctx) {
        ArrayAccess arrayAccess = ast.newArrayAccess();
        arrayAccess.setArray((Expression) ctx.array.accept(this));
        arrayAccess.setIndex((Expression) ctx.index.accept(this));
        return arrayAccess;
    }

    @Override
    public MethodInvocation visitMethodCallExpr(JavaParser.MethodCallExprContext ctx) {
        return (MethodInvocation) ctx.methodCall().accept(this);
    }

    @Override
    public MethodInvocation visitMethodCall(JavaParser.MethodCallContext ctx) {
        MethodInvocation methodInvocation = ast.newMethodInvocation();
        methodInvocation.setName(getIdentifier(ctx.IDENTIFIER()));
        if (ctx.expressionList() != null) {
            methodInvocation.arguments().addAll(createList(ctx.expressionList().expression()));
        }
        return methodInvocation;

    }

    @Override
    public Expression visitNewExpr(JavaParser.NewExprContext ctx) {
        return (Expression) ctx.creator().accept(this);
    }

    @Override
    public Expression visitCreator(JavaParser.CreatorContext ctx) {
        if (ctx.arrayCreatorRest() != null) { // array creation
            ArrayCreation arrayCreation = ast.newArrayCreation();
            int dimensions = getDimensions(ctx.arrayCreatorRest());
            arrayCreation.setType(ast.newArrayType((Type) ctx.createdName().accept(this), dimensions));
            if (ctx.arrayCreatorRest().expression() != null) {
                arrayCreation.dimensions().addAll(createList(ctx.arrayCreatorRest().expression()));
            }
            if (ctx.arrayCreatorRest().arrayInitializer() != null) {
                arrayCreation.setInitializer((ArrayInitializer) ctx.arrayCreatorRest().arrayInitializer().accept(this));
            }
            return arrayCreation;
        }
        else { // class creation
            ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();
            if (ctx.nonWildcardTypeArguments() != null) {
                classInstanceCreation.typeArguments().addAll(createList(ctx.nonWildcardTypeArguments().typeList().typeType()));
            }
            classInstanceCreation.setType((Type) ctx.createdName().accept(this));
            classInstanceCreation.arguments().addAll(getArguments(ctx.classCreatorRest().arguments()));
            if (ctx.classCreatorRest().classBody() != null) {
                AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                anonymousClassDeclaration.bodyDeclarations().addAll(createList(ctx.classCreatorRest().classBody().classBodyDeclaration()));
                classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
            }
            return classInstanceCreation;
        }
    }

    @Override
    public Type visitCreatedName(JavaParser.CreatedNameContext ctx) {
        if (ctx.primitiveType() != null) {
            return (PrimitiveType) ctx.primitiveType().accept(this);
        }
        // Unifiy this one and ClassOrInterfaceType
        else {
            int i = 0;
            Type type = ast.newSimpleType(ast.newSimpleName(ctx.IDENTIFIER().get(i++).getText()));

            if (ctx.getChildCount() > 1 && ctx.getChild(i) instanceof JavaParser.TypeArgumentsOrDiamondContext) {

                ParameterizedType parameterizedType = ast.newParameterizedType(type);
                parameterizedType.typeArguments().addAll(createList(((JavaParser.TypeArgumentsOrDiamondContext) ctx.getChild(i++)).typeArguments().typeArgument()));
                type = parameterizedType;
            }

            while (true) {
                if (i >= ctx.getChildCount()) break;
                if (isIdentifier(ctx.getChild(i))) {
                    type = ast.newQualifiedType(type, ast.newSimpleName(ctx.getChild(i).getText()));
                    if (i + 1 < ctx.getChildCount() && ctx.getChild(i + 1) instanceof JavaParser.TypeArgumentsOrDiamondContext) {
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
    }

    @Override
    public PrimitiveType visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
        return ast.newPrimitiveType(PrimitiveType.toCode(ctx.getText()));
    }

    @Override
    public ASTNode visitTypeCastExpr(JavaParser.TypeCastExprContext ctx) {
        CastExpression castExpression = ast.newCastExpression();
        castExpression.setType((Type) ctx.typeType().accept(this));
        castExpression.setExpression((Expression) ctx.expression().accept(this));
        return castExpression;
    }

    @Override
    public ASTNode visitPrefixExpr(JavaParser.PrefixExprContext ctx) {
        PrefixExpression prefixExpression = ast.newPrefixExpression();
        prefixExpression.setOperand((Expression) ctx.expression().accept(this));
        prefixExpression.setOperator(PrefixExpression.Operator.toOperator(ctx.prefix.getText()));
        return prefixExpression;
    }

    @Override
    public ASTNode visitPostfixExpr(JavaParser.PostfixExprContext ctx) {
        PostfixExpression postfixExpression = ast.newPostfixExpression();
        postfixExpression.setOperand((Expression) ctx.expression().accept(this));
        postfixExpression.setOperator(PostfixExpression.Operator.toOperator(ctx.postfix.getText()));
        return postfixExpression;
    }

    @Override
    public ASTNode visitInfixExpr(JavaParser.InfixExprContext ctx) {
        InfixExpression infixExpression = ast.newInfixExpression();
        infixExpression.setLeftOperand((Expression) ctx.left.accept(this));
        infixExpression.setRightOperand((Expression) ctx.right.accept(this));
        if (ctx.bop != null) {
            infixExpression.setOperator(InfixExpression.Operator.toOperator(ctx.bop.getText()));
        }
        else { // shiftOp
            infixExpression.setOperator(InfixExpression.Operator.toOperator(ctx.shiftOp().getText()));
        }

        return infixExpression;
    }

    @Override
    public ASTNode visitAssignmentExpr(JavaParser.AssignmentExprContext ctx) {
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) ctx.left.accept(this));
        assignment.setRightHandSide((Expression) ctx.right.accept(this));
        assignment.setOperator(Assignment.Operator.toOperator(ctx.bop.getText()));
        return assignment;
    }

    @Override
    public ASTNode visitInstanceofExpr(JavaParser.InstanceofExprContext ctx) {
        InstanceofExpression instanceofExpression = ast.newInstanceofExpression();
        instanceofExpression.setLeftOperand((Expression) ctx.expression().accept(this));
        instanceofExpression.setRightOperand((Type) ctx.typeType().accept(this));
        return instanceofExpression;
    }

    @Override
    public ASTNode visitTernaryExpr(JavaParser.TernaryExprContext ctx) {
        ConditionalExpression conditionalExpression = ast.newConditionalExpression();
        conditionalExpression.setExpression((Expression) ctx.expr.accept(this));
        conditionalExpression.setThenExpression((Expression) ctx.thenBranch.accept(this));
        conditionalExpression.setElseExpression((Expression) ctx.elseBranch.accept(this));
        return conditionalExpression;
    }

    @Override
    public Expression visitLiteral(JavaParser.LiteralContext ctx) {
        if (ctx.BOOL_LITERAL() != null) {
            return ast.newBooleanLiteral(Boolean.parseBoolean(ctx.BOOL_LITERAL().getText()));
        }
        else if (ctx.CHAR_LITERAL() != null) {
            CharacterLiteral characterLiteral = ast.newCharacterLiteral();
            characterLiteral.setCharValue(ctx.CHAR_LITERAL().getText().charAt(1));
            return characterLiteral;
        }
        else if (ctx.NULL_LITERAL() != null) {
            return ast.newNullLiteral();
        }
        else if (ctx.STRING_LITERAL() != null) {
            StringLiteral stringLiteral = ast.newStringLiteral();
            String text = ctx.STRING_LITERAL().getText();
            stringLiteral.setLiteralValue(text.substring(1, text.length() - 1));
            return stringLiteral;
        }
        else  if (ctx.integerLiteral() != null) {
            return ast.newNumberLiteral(ctx.integerLiteral().getText());
        }
        else { // float literal
            return ast.newNumberLiteral(ctx.floatLiteral().getText());
        }
    }

    @Override
    public Expression visitParExpression(JavaParser.ParExpressionContext ctx) {
        ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
        parenthesizedExpression.setExpression((Expression) ctx.expression().accept(this));
        return parenthesizedExpression;
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
        annotationTypeDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
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
        return singleVariableDeclaration;
    }

    @Override
    public ASTNode visitBlockStmt(JavaParser.BlockStmtContext ctx) {
        return ctx.block().accept(this);
    }

    @Override
    public ASTNode visitAssertStmt(JavaParser.AssertStmtContext ctx) {
        AssertStatement assertStatement = ast.newAssertStatement();
        assertStatement.setExpression((Expression) ctx.expr.accept(this));
        if (ctx.message != null) {
            assertStatement.setMessage((Expression) ctx.message.accept(this));
        }
        return assertStatement;
    }

    @Override
    public ASTNode visitIfStmt(JavaParser.IfStmtContext ctx) {
        IfStatement ifStatement = ast.newIfStatement();
        ifStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));
        ifStatement.setThenStatement((Statement) ctx.thenBranch.accept(this));
        if (ctx.elseBranch != null) {
            ifStatement.setElseStatement((Statement) ctx.elseBranch.accept(this));
        }
        return ifStatement;
    }

    @Override
    public ASTNode visitForStmt(JavaParser.ForStmtContext ctx) {
        if (ctx.forControl().enhancedForControl() != null) {
            EnhancedForStatement forStatement = ast.newEnhancedForStatement();

            JavaParser.EnhancedForControlContext enhancedForControlContext = ctx.forControl().enhancedForControl();
            forStatement.setExpression((Expression) enhancedForControlContext.expression().accept(this));

            SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
            singleVariableDeclaration.setType((Type) enhancedForControlContext.typeType().accept(this));
            singleVariableDeclaration.setName(getIdentifier(enhancedForControlContext.variableDeclaratorId().IDENTIFIER()));
            singleVariableDeclaration.modifiers().addAll(createList(enhancedForControlContext.variableModifier()));

            forStatement.setParameter(singleVariableDeclaration);

            forStatement.setBody((Statement) ctx.statement().accept(this));
            return forStatement;
        } else {
            ForStatement forStatement = ast.newForStatement();

            if (ctx.forControl().forInit() != null) {
                if (ctx.forControl().forInit().localVariableDeclaration() != null) {
                    forStatement.initializers().add(ctx.forControl().forInit().accept(this));
                } else {
                    forStatement.initializers().addAll(createList(ctx.forControl().forInit().expressionList().expression()));
                }
            }

            for (int i = 0, j = 2; i < 10; i++)

            if (ctx.forControl().expression() != null) {
                forStatement.setExpression((Expression) ctx.forControl().expression().accept(this));
            }

            if (ctx.forControl().forUpdate != null) {
                forStatement.updaters().addAll(createList(ctx.forControl().forUpdate.expression()));
            }
            forStatement.setBody((Statement) ctx.statement().accept(this));
            return forStatement;
        }
    }

    @Override
    public WhileStatement visitWhileStmt(JavaParser.WhileStmtContext ctx) {
        WhileStatement whileStatement = ast.newWhileStatement();
        whileStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));
        whileStatement.setBody((Statement) ctx.statement().accept(this));
        return whileStatement;
    }

    @Override
    public ASTNode visitDoStmt(JavaParser.DoStmtContext ctx) {
        DoStatement doStatement = ast.newDoStatement();
        doStatement.setExpression((Expression) ctx.parExpression().accept(this));
        doStatement.setBody((Statement) ctx.statement().accept(this));
        return doStatement;
    }

    @Override
    public ASTNode visitTryStmt(JavaParser.TryStmtContext ctx) {
        TryStatement tryStatement = ast.newTryStatement();
        tryStatement.setBody((Block) ctx.block().accept(this));
        tryStatement.catchClauses().addAll(createList(ctx.catchClause()));

        if (ctx.finallyBlock() != null) {
            tryStatement.setFinally((Block) ctx.finallyBlock().accept(this));
        }
        return tryStatement;
    }

    @Override
    public ASTNode visitTryWithResourcesStmt(JavaParser.TryWithResourcesStmtContext ctx) {
        TryStatement tryStatement = ast.newTryStatement();
        tryStatement.setBody((Block) ctx.block().accept(this));
        tryStatement.catchClauses().addAll(createList(ctx.catchClause()));
        tryStatement.resources().addAll(createList(ctx.resourceSpecification().resources().resource()));

        if (ctx.finallyBlock() != null) {
            tryStatement.setFinally((Block) ctx.finallyBlock().accept(this));
        }
        return tryStatement;
    }

    @Override
    public VariableDeclarationExpression visitResource(JavaParser.ResourceContext ctx) {
        VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
        variableDeclarationFragment.setName(ast.newSimpleName(ctx.variableDeclaratorId().IDENTIFIER().getText()));
        variableDeclarationFragment.setInitializer((Expression) ctx.expression().accept(this));
        VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(variableDeclarationFragment);
        variableDeclarationExpression.modifiers().addAll(createList(ctx.variableModifier()));
        variableDeclarationExpression.setType((Type) ctx.classOrInterfaceType().accept(this));
        return variableDeclarationExpression;
    }

    @Override
    public CatchClause visitCatchClause(JavaParser.CatchClauseContext ctx) {
        CatchClause catchClause = ast.newCatchClause();

        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.modifiers().addAll(createList(ctx.variableModifier()));
        singleVariableDeclaration.setType((Type) ctx.catchType().accept(this));
        singleVariableDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        catchClause.setException(singleVariableDeclaration);

        catchClause.setBody((Block) ctx.block().accept(this));

        return catchClause;
    }

    @Override
    public Type visitCatchType(JavaParser.CatchTypeContext ctx) {
        UnionType unionType = ast.newUnionType();
        unionType.types().addAll(createList(ctx.qualifiedName()).stream().map(name -> convertNameToType((Name) name)).collect(toList()));
        return unionType;
    }

    @Override
    public SwitchStatement visitSwitchStmt(JavaParser.SwitchStmtContext ctx) {
        SwitchStatement switchStatement = ast.newSwitchStatement();
        switchStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));

        List<Statement> statements = ctx.switchBlockStatementGroup().stream().flatMap(stmt -> getStatements(stmt).stream()).collect(toList());
        switchStatement.statements().addAll(statements);
        return switchStatement;
    }

    @Override
    public SwitchCase visitSwitchLabel(JavaParser.SwitchLabelContext ctx) {
        SwitchCase switchCase = ast.newSwitchCase();
        switchCase.setExpression(null); // default case
        if (ctx.expression() != null) {
            switchCase.setExpression((Expression) ctx.constantExpression.accept(this));
        }
        else if (ctx.enumConstantName != null) {
            switchCase.setExpression(ast.newSimpleName(ctx.enumConstantName.getText()));
        }
        return switchCase;
    }

    @Override
    public ASTNode visitSynchronizedStmt(JavaParser.SynchronizedStmtContext ctx) {
        SynchronizedStatement synchronizedStatement = ast.newSynchronizedStatement();
        synchronizedStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));
        synchronizedStatement.setBody((Block) ctx.block().accept(this));
        return synchronizedStatement;
    }

    @Override
    public ASTNode visitReturnStmt(JavaParser.ReturnStmtContext ctx) {
        ReturnStatement returnStatement = ast.newReturnStatement();
        if (ctx.expression() != null) {
            returnStatement.setExpression((Expression) ctx.expression().accept(this));
        }
        return returnStatement;
    }

    @Override
    public ASTNode visitThrowStmt(JavaParser.ThrowStmtContext ctx) {
        ThrowStatement throwStatement = ast.newThrowStatement();
        throwStatement.setExpression((Expression) ctx.expression().accept(this));
        return throwStatement;
    }

    @Override
    public ASTNode visitBreakStmt(JavaParser.BreakStmtContext ctx) {
        BreakStatement breakStatement = ast.newBreakStatement();
        if (ctx.IDENTIFIER() != null) {
            breakStatement.setLabel(getIdentifier(ctx.IDENTIFIER()));
        }
        return breakStatement;
    }

    @Override
    public ASTNode visitContinueStmt(JavaParser.ContinueStmtContext ctx) {
        ContinueStatement continueStatement = ast.newContinueStatement();
        if (ctx.IDENTIFIER() != null) {
            continueStatement.setLabel(getIdentifier(ctx.IDENTIFIER()));
        }
        return continueStatement;
    }

    @Override
    public ASTNode visitExpressionStmt(JavaParser.ExpressionStmtContext ctx) {
        return ast.newExpressionStatement((Expression) ctx.statementExpression.accept(this));
    }

    @Override
    public Expression visitFieldAccessExpr(JavaParser.FieldAccessExprContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            Expression expression = (Expression) ctx.expression().accept(this);
            if (expression instanceof SuperFieldAccess) {
                ((SuperFieldAccess) expression).setName(getIdentifier(ctx.IDENTIFIER()));
                return expression;
            } else {
                FieldAccess fieldAccess = ast.newFieldAccess();
                fieldAccess.setExpression(expression);
                fieldAccess.setName(getIdentifier(ctx.IDENTIFIER()));
                return fieldAccess;
            }
        }
        else if (ctx.THIS() != null) {
            ThisExpression thisExpression = ast.newThisExpression();
            thisExpression.setQualifier((Name) ctx.expression().accept(this));
            return thisExpression;
        }
        else if (ctx.methodCall() != null) {
            MethodInvocation methodInvocation = (MethodInvocation) ctx.methodCall().accept(this);
            methodInvocation.setExpression((Expression) ctx.expression().accept(this));
            return methodInvocation;
        }
        else if (ctx.SUPER() != null) {
            if (ctx.superSuffix().IDENTIFIER() != null) {
                SuperFieldAccess superFieldAccess = ast.newSuperFieldAccess();
                superFieldAccess.setQualifier((Name) ctx.expression().accept(this));
                superFieldAccess.setName(ast.newSimpleName(ctx.superSuffix().IDENTIFIER().getText()));
            } else {
                SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
                superMethodInvocation.arguments().addAll(getArguments(ctx.superSuffix().arguments()));
                return superMethodInvocation;
            }
        }
        else if (ctx.NEW() != null) {
            ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();
            if (ctx.innerCreator().classCreatorRest().classBody() != null) {
                AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                anonymousClassDeclaration.bodyDeclarations().addAll(createList(ctx.innerCreator().classCreatorRest().classBody().classBodyDeclaration()));
                classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
            }

            classInstanceCreation.setExpression((Expression) ctx.expression().accept(this));
            classInstanceCreation.setType(ast.newSimpleType(getIdentifier(ctx.innerCreator().IDENTIFIER())));
            classInstanceCreation.arguments().addAll(getArguments(ctx.innerCreator().classCreatorRest().arguments()));
            if (ctx.nonWildcardTypeArguments() != null) {
                classInstanceCreation.typeArguments().addAll(createList(ctx.nonWildcardTypeArguments().typeList().typeType()));
            }

            return classInstanceCreation;
        }

        throw new RuntimeException();
    }

    @Override
    public ASTNode visitLabelStmt(JavaParser.LabelStmtContext ctx) {
        LabeledStatement labeledStatement = ast.newLabeledStatement();
        labeledStatement.setLabel(getIdentifier(ctx.IDENTIFIER()));
        labeledStatement.setBody((Statement) ctx.statement().accept(this));
        return labeledStatement;
    }

    private <T extends ParserRuleContext> List<ASTNode> createList(List<T> list) {
        return list.stream().map(annotation -> annotation.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private <T extends ParserRuleContext, U> List<U> createList(List<T> list, Class<U> clazz) {
        return list.stream().map(annotation -> (U) annotation.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private boolean isIdentifier(ParseTree node) {
        return node instanceof TerminalNode && ((TerminalNode) node).getSymbol().getType() == JavaLexer.IDENTIFIER;
    }

    private int getDimensions(JavaParser.ArrayCreatorRestContext ctx) {
        if (ctx.dimensions() == null || ctx.dimensions().children == null) {
            return 0;
        }
        return ctx.dimensions().children.size();
    }

    private SimpleName getIdentifier(TerminalNode node) {
        return ast.newSimpleName(node.getText());
    }

    private List<Expression> getArguments(JavaParser.ArgumentsContext arguments) {
        if (arguments.expressionList() == null) {
            return Collections.emptyList();
        }
        return createList(arguments.expressionList().expression(), Expression.class);
    }

    private List<Statement> getStatements(JavaParser.SwitchBlockStatementGroupContext ctx) {
        List<Statement> switchLabels = createList(ctx.switchLabel(), Statement.class);
        List<Statement> blockStatements = createList(ctx.blockStatement(), Statement.class);
        List<Statement> result = new ArrayList<>(switchLabels);
        result.addAll(blockStatements);
        return result;
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
