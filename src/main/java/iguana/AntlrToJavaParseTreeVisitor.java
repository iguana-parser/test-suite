package iguana;

import antlr4java.JavaLexer;
import antlr4java.JavaParser;
import antlr4java.JavaParserBaseVisitor;;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class AntlrToJavaParseTreeVisitor extends JavaParserBaseVisitor<ASTNode> {

    private AST ast = AST.newAST(AST.JLS10);

    @Override
    public CompilationUnit visitCompilationUnit(JavaParser.CompilationUnitContext ctx) {
        CompilationUnit compilationUnit = ast.newCompilationUnit();
        if (ctx.packageDeclaration() != null) {
            compilationUnit.setPackage((PackageDeclaration) ctx.packageDeclaration().accept(this));
        }
        compilationUnit.imports().addAll(createList(ctx.importDeclaration()));
        compilationUnit.types().addAll(ctx.typeDeclaration().stream().map(td -> td.accept(this)).filter(Objects::nonNull).collect(toList()));
        return compilationUnit;
    }

    @Override
    public PackageDeclaration visitPackageDeclaration(JavaParser.PackageDeclarationContext ctx) {
        PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
        packageDeclaration.annotations().addAll(createList(ctx.annotation()));
        packageDeclaration.setName((Name) ctx.qualifiedName().accept(this));
        return packageDeclaration;
    }

    @Override
    public ImportDeclaration visitImportDeclaration(JavaParser.ImportDeclarationContext ctx) {
        ImportDeclaration importDeclaration = ast.newImportDeclaration();
        if (ctx.STATIC() != null) {
            importDeclaration.setStatic(true);
        }
        importDeclaration.setName((Name) ctx.qualifiedName().accept(this));
        if (ctx.onDemand() != null) {
            importDeclaration.setOnDemand(true);
        }
        return importDeclaration;
    }

    @Override
    public BodyDeclaration visitClassOrInterfaceTypeDeclaration(JavaParser.ClassOrInterfaceTypeDeclarationContext ctx) {
        BodyDeclaration bodyDeclaration;

        if (ctx.classDeclaration() != null) {
            bodyDeclaration = (BodyDeclaration) ctx.classDeclaration().accept(this);
        } else if (ctx.interfaceDeclaration() != null) {
            bodyDeclaration = (BodyDeclaration) ctx.interfaceDeclaration().accept(this);
        } else if (ctx.enumDeclaration() != null) {
            bodyDeclaration = (BodyDeclaration) ctx.enumDeclaration().accept(this);
        } else if (ctx.annotationTypeDeclaration() != null) {
            bodyDeclaration = (BodyDeclaration) ctx.annotationTypeDeclaration().accept(this);
        } else {
            throw new RuntimeException("Unexpected type declaration");
        }

        bodyDeclaration.modifiers().addAll(createList(ctx.classOrInterfaceModifier()));
        return bodyDeclaration;
    }

    @Override
    public Annotation visitAnnotation(JavaParser.AnnotationContext ctx) {
        Annotation annotation;

        if (ctx.values() == null) {
            annotation = ast.newMarkerAnnotation();
        } else if (ctx.values().elementValue() != null) { // SingleValueAnnotation
            annotation = ast.newSingleMemberAnnotation();
            ((SingleMemberAnnotation) annotation).setValue((Expression) ctx.values().elementValue().accept(this));
        } else if (ctx.values().elementValuePairs() != null) {
            annotation = ast.newNormalAnnotation();
            ((NormalAnnotation) annotation).values().addAll(createList(ctx.values().elementValuePairs().elementValuePair()));
        } else {
            annotation = ast.newNormalAnnotation();
        }

        annotation.setTypeName((Name) ctx.qualifiedName().accept(this));
        return annotation;
    }

    @Override
    public ArrayInitializer visitElementValueArrayInitializer(JavaParser.ElementValueArrayInitializerContext ctx) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll(createList(ctx.elementValue()));
        return arrayInitializer;
    }

    @Override
    public MemberValuePair visitElementValuePair(JavaParser.ElementValuePairContext ctx) {
        MemberValuePair memberValuePair = ast.newMemberValuePair();
        memberValuePair.setName(getIdentifier(ctx.IDENTIFIER()));
        memberValuePair.setValue((Expression) ctx.elementValue().accept(this));
        return memberValuePair;
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
            JavaParser.TypeArgumentsContext typeArguments = (JavaParser.TypeArgumentsContext) ctx.getChild(i);
            parameterizedType.typeArguments().addAll(createList(typeArguments.typeArgument()));
            type = parameterizedType;
            i++;
        }

        while (true) {
            if (i >= ctx.getChildCount()) break;
            if (ctx.getChild(i).getText().equals(".")) {
                i++;
            }
            if (isIdentifier(ctx.getChild(i))) {
                type = ast.newQualifiedType(type, ast.newSimpleName(ctx.getChild(i).getText()));
                if (i + 1 < ctx.getChildCount() && ctx.getChild(i + 1) instanceof JavaParser.TypeArgumentsContext) {
                    type = ast.newParameterizedType(type);
                    JavaParser.TypeArgumentsContext typeArguments = (JavaParser.TypeArgumentsContext) ctx.getChild(i + 1);
                    if (typeArguments.typeArgument() != null) {
                        ((ParameterizedType) type).typeArguments().addAll(createList(typeArguments.typeArgument()));
                    }
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
        if (ctx.SUPER() != null) {
            wildcardType.setUpperBound(false);
        }
        return wildcardType;
    }

    @Override
    public TypeDeclaration visitInterfaceDeclaration(JavaParser.InterfaceDeclarationContext ctx) {
        TypeDeclaration interfaceDeclaration = ast.newTypeDeclaration();
        interfaceDeclaration.setInterface(true);
        interfaceDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        if (ctx.typeList() != null) {
            interfaceDeclaration.superInterfaceTypes().addAll(createList(ctx.typeList().typeType()));
        }
        interfaceDeclaration.bodyDeclarations().addAll(createList(ctx.interfaceBody().interfaceBodyDeclaration()));
        if (ctx.typeParameters() != null) {
            interfaceDeclaration.typeParameters().addAll(createList(ctx.typeParameters().typeParameter()));
        }
        return interfaceDeclaration;
    }

    @Override
    public BodyDeclaration visitInterfaceBodyDeclaration(JavaParser.InterfaceBodyDeclarationContext ctx) {
        if (ctx.interfaceMemberDeclaration() != null) {
            BodyDeclaration bodyDeclaration = (BodyDeclaration) ctx.interfaceMemberDeclaration().accept(this);
            bodyDeclaration.modifiers().addAll(createList(ctx.modifier()));
            return bodyDeclaration;
        }
        return null;
    }

    @Override
    public BodyDeclaration visitConstDeclaration(JavaParser.ConstDeclarationContext ctx) {
        List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(ctx.constantDeclarator());
        FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
        fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
        fieldDeclaration.setType((Type) ctx.typeType().accept(this));
        return fieldDeclaration;
    }

    @Override
    public MethodDeclaration visitInterfaceMethodDeclaration(JavaParser.InterfaceMethodDeclarationContext ctx) {
        MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
        methodDeclaration.modifiers().addAll(createList(ctx.interfaceMethodModifier()));
        if (ctx.typeParameters() != null) {
            methodDeclaration.typeParameters().addAll(createList(ctx.typeParameters().typeParameter()));
        }
        methodDeclaration.setReturnType2((Type) ctx.typeTypeOrVoid().accept(this));
        methodDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        methodDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        if (ctx.qualifiedNameList() != null) {
            methodDeclaration.thrownExceptionTypes().addAll(createList(ctx.qualifiedNameList().qualifiedName()).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList()));
        }

        if (ctx.methodBody() != null) {
            methodDeclaration.setBody((Block) ctx.methodBody().accept(this));
        }
        return methodDeclaration;
    }

    @Override
    public ASTNode visitEnumDeclaration(JavaParser.EnumDeclarationContext ctx) {
        EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
        enumDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        if (ctx.typeList() != null) {
            enumDeclaration.superInterfaceTypes().addAll(createList(ctx.typeList().typeType()));
        }

        if (ctx.enumConstants() != null) {
            enumDeclaration.enumConstants().addAll(createList(ctx.enumConstants().enumConstant()));
        }

        if (ctx.enumBodyDeclarations() != null) {
            enumDeclaration.bodyDeclarations().addAll(createList(ctx.enumBodyDeclarations().classBodyDeclaration()));
        }
        return enumDeclaration;
    }

    @Override
    public EnumConstantDeclaration visitEnumConstant(JavaParser.EnumConstantContext ctx) {
        EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

        enumConstantDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));

        enumConstantDeclaration.modifiers().addAll(createList(ctx.annotation()));

        if (ctx.arguments() != null) {
            enumConstantDeclaration.arguments().addAll(getArguments(ctx.arguments()));
        }

        if (ctx.classBody() != null) {
            AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
            anonymousClassDeclaration.bodyDeclarations().addAll(createList(ctx.classBody().classBodyDeclaration()));
            enumConstantDeclaration.setAnonymousClassDeclaration(anonymousClassDeclaration);
        }

        return enumConstantDeclaration;
    }

    @Override
    public BodyDeclaration visitClassBodyDeclaration(JavaParser.ClassBodyDeclarationContext ctx) {
        if (ctx.block() != null) {
            Initializer initializer = ast.newInitializer();
            if (ctx.STATIC() != null) {
                initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
            }
            Block body = (Block) ctx.block().accept(this);
            if (body != null) {
                initializer.setBody(body);
            }
            return initializer;
        } else if (ctx.memberDeclaration() != null) {
            BodyDeclaration bodyDeclaration = (BodyDeclaration) ctx.memberDeclaration().accept(this);
            bodyDeclaration.modifiers().addAll(createList(ctx.modifier()));
            return bodyDeclaration;
        } else {
            return null;
        }
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
            methodDeclaration.thrownExceptionTypes().addAll(createList(ctx.qualifiedNameList().qualifiedName()).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList()));
        }

        if (ctx.methodBody() != null) {
            methodDeclaration.setBody((Block) ctx.methodBody().accept(this));
        }

        methodDeclaration.extraDimensions().addAll(getDimensions(ctx.dimensions()));
        return methodDeclaration;
    }

    @Override
    public MethodDeclaration visitGenericMethodDeclaration(JavaParser.GenericMethodDeclarationContext ctx) {
        MethodDeclaration methodDeclaration = (MethodDeclaration) ctx.methodDeclaration().accept(this);
        methodDeclaration.typeParameters().addAll(createList(ctx.typeParameters().typeParameter()));
        return methodDeclaration;
    }

    @Override
    public MethodDeclaration visitGenericConstructorDeclaration(JavaParser.GenericConstructorDeclarationContext ctx) {
        MethodDeclaration methodDeclaration = (MethodDeclaration) ctx.constructorDeclaration().accept(this);
        methodDeclaration.typeParameters().addAll(createList(ctx.typeParameters().typeParameter()));
        return methodDeclaration;
    }

    @Override
    public ASTNode visitFieldDeclaration(JavaParser.FieldDeclarationContext ctx) {
        List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(ctx.variableDeclarators());
        FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
        fieldDeclaration.setType((Type) ctx.typeType().accept(this));
        fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
        return fieldDeclaration;
    }

    @Override
    public ASTNode visitConstructorDeclaration(JavaParser.ConstructorDeclarationContext ctx) {
        MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
        constructorDeclaration.setConstructor(true);
        constructorDeclaration.setReturnType2(null);
        constructorDeclaration.parameters().addAll(getFormalParameters(ctx.formalParameters()));
        constructorDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        if (ctx.qualifiedNameList() != null) {
            List<Type> types = createList(ctx.qualifiedNameList().qualifiedName()).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList());
            constructorDeclaration.thrownExceptionTypes().addAll(types);
        }
        constructorDeclaration.setBody((Block) ctx.constructorBody().accept(this));
        return constructorDeclaration;
    }

    @Override
    public ASTNode visitConstructorBody(JavaParser.ConstructorBodyContext ctx) {
        Block block = ast.newBlock();
        if (ctx.explicitConstructorInvocation() != null) {
            block.statements().add(ctx.explicitConstructorInvocation().accept(this));
        }
        block.statements().addAll(createList(ctx.blockStatement()));
        return block;
    }

    @Override
    public ConstructorInvocation visitConstructorInvocation(JavaParser.ConstructorInvocationContext ctx) {
        ConstructorInvocation constructorInvocation = ast.newConstructorInvocation();
        if (ctx.nonWildcardTypeArguments() != null) {
            constructorInvocation.typeArguments().addAll(createList(ctx.nonWildcardTypeArguments().typeList().typeType()));
        }
        if (ctx.arguments() != null) {
            constructorInvocation.arguments().addAll(getArguments(ctx.arguments()));
        }
        return constructorInvocation;
    }

    @Override
    public SuperConstructorInvocation visitSuperConstructorInvocation(JavaParser.SuperConstructorInvocationContext ctx) {
        SuperConstructorInvocation superConstructorInvocation = ast.newSuperConstructorInvocation();
        if (ctx.primary() != null) {
            superConstructorInvocation.setExpression((Expression) ctx.primary().accept(this));
        }
        if (ctx.nonWildcardTypeArguments() != null) {
            superConstructorInvocation.typeArguments().addAll(createList(ctx.nonWildcardTypeArguments().typeList().typeType()));
        }
        if (ctx.arguments() != null) {
            superConstructorInvocation.arguments().addAll(getArguments(ctx.arguments()));
        }
        return superConstructorInvocation;
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
        singleVariableDeclaration.extraDimensions().addAll(getDimensions(ctx.variableDeclaratorId().dimensions()));
        singleVariableDeclaration.setName(getIdentifier(ctx.variableDeclaratorId().IDENTIFIER()));
        return singleVariableDeclaration;
    }

    @Override
    public ASTNode visitBlockStatement(JavaParser.BlockStatementContext ctx) {
        if (ctx.localVariableDeclaration() != null) {
            List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(ctx.localVariableDeclaration().variableDeclarators());
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
        TypeDeclaration typeDeclaration = (TypeDeclaration) ctx.classDeclaration().accept(this);
        typeDeclaration.modifiers().addAll(createList(ctx.classOrInterfaceModifier()));
        return ast.newTypeDeclarationStatement(typeDeclaration);
    }

    @Override
    public ArrayInitializer visitArrayInitializer(JavaParser.ArrayInitializerContext ctx) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll(createList(ctx.variableInitializer()));
        return arrayInitializer;
    }

    @Override
    public Expression visitPrimary(JavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) {
            ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
            parenthesizedExpression.setExpression((Expression) ctx.expression().accept(this));
            return parenthesizedExpression;
        } else if (ctx.THIS() != null) {
            ThisExpression thisExpression = ast.newThisExpression();
            if (ctx.qualifiedName() != null) {
                thisExpression.setQualifier((Name) ctx.qualifiedName().accept(this));
            }
            return thisExpression;
        } else if (ctx.SUPER() != null) {
            return ast.newSuperFieldAccess();
        } else if (ctx.literal() != null) {
            return (Expression) ctx.literal().accept(this);
        } else if (ctx.IDENTIFIER() != null) {
            return getIdentifier(ctx.IDENTIFIER());
        } else if (ctx.typeTypeOrVoid() != null) {
            TypeLiteral typeLiteral = ast.newTypeLiteral();
            typeLiteral.setType((Type) ctx.typeTypeOrVoid().accept(this));
            return typeLiteral;
        } else {
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
            if (ctx.arrayCreatorRest().expression() != null) {
                List<ASTNode> expressions = createList(ctx.arrayCreatorRest().expression());
                int dimensions = getDimensionsSize(ctx.arrayCreatorRest().dimensions()) + expressions.size();
                arrayCreation.setType(ast.newArrayType((Type) ctx.createdName().accept(this), dimensions));
                arrayCreation.dimensions().addAll(expressions);
            }
            if (ctx.arrayCreatorRest().arrayInitializer() != null) {
                int dimensions = getDimensionsSize(ctx.arrayCreatorRest().dimensions()) + 1;
                arrayCreation.setType(ast.newArrayType((Type) ctx.createdName().accept(this), dimensions));
                arrayCreation.setInitializer((ArrayInitializer) ctx.arrayCreatorRest().arrayInitializer().accept(this));
            }
            return arrayCreation;
        } else { // class creation
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
        // Unify this one and ClassOrInterfaceType
        else {
            int i = 0;
            Type type = ast.newSimpleType(ast.newSimpleName(ctx.IDENTIFIER().get(i++).getText()));

            if (ctx.getChildCount() > 1 && ctx.getChild(i) instanceof JavaParser.TypeArgumentsOrDiamondContext) {
                ParameterizedType parameterizedType = ast.newParameterizedType(type);
                JavaParser.TypeArgumentsOrDiamondContext typeArgumentsOrDiamondContext = (JavaParser.TypeArgumentsOrDiamondContext) ctx.getChild(i);
                if (typeArgumentsOrDiamondContext.typeArguments() != null) {
                    parameterizedType.typeArguments().addAll(createList(typeArgumentsOrDiamondContext.typeArguments().typeArgument()));
                }
                type = parameterizedType;
            }

            while (true) {
                if (i >= ctx.getChildCount()) break;
                if (ctx.getChild(i).getText().equals(".")) {
                    i++;
                }
                if (isIdentifier(ctx.getChild(i))) {
                    type = ast.newQualifiedType(type, ast.newSimpleName(ctx.getChild(i).getText()));
                    if (i + 1 < ctx.getChildCount() && ctx.getChild(i + 1) instanceof JavaParser.TypeArgumentsOrDiamondContext) {
                        type = ast.newParameterizedType(type);
                        JavaParser.TypeArgumentsOrDiamondContext typeArgumentsOrDiamondContext = (JavaParser.TypeArgumentsOrDiamondContext) ctx.getChild(i + 1);
                        if (typeArgumentsOrDiamondContext.typeArguments() != null) {
                            ((ParameterizedType) type).typeArguments().addAll(createList(typeArgumentsOrDiamondContext.typeArguments().typeArgument()));
                        }
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
    public PostfixExpression visitPostfixExpr(JavaParser.PostfixExprContext ctx) {
        PostfixExpression postfixExpression = ast.newPostfixExpression();
        postfixExpression.setOperand((Expression) ctx.expression().accept(this));
        postfixExpression.setOperator(PostfixExpression.Operator.toOperator(ctx.postfix.getText()));
        return postfixExpression;
    }

    @Override
    public InfixExpression visitInfixExpr(JavaParser.InfixExprContext ctx) {
        InfixExpression infixExpression = ast.newInfixExpression();
        Expression leftOperand = (Expression) ctx.left.accept(this);
        Expression rightOperand = (Expression) ctx.right.accept(this);
        InfixExpression.Operator operator;
        if (ctx.bop != null) {
            operator = InfixExpression.Operator.toOperator(ctx.bop.getText());
        } else { // shiftOp
            operator = InfixExpression.Operator.toOperator(ctx.shiftOp().getText());
        }

        infixExpression.setLeftOperand(leftOperand);
        infixExpression.setRightOperand(rightOperand);
        infixExpression.setOperator(operator);

        return infixExpression;
    }

    @Override
    public Assignment visitAssignmentExpr(JavaParser.AssignmentExprContext ctx) {
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) ctx.left.accept(this));
        assignment.setRightHandSide((Expression) ctx.right.accept(this));
        assignment.setOperator(Assignment.Operator.toOperator(ctx.bop.getText()));
        return assignment;
    }

    @Override
    public InstanceofExpression visitInstanceofExpr(JavaParser.InstanceofExprContext ctx) {
        InstanceofExpression instanceofExpression = ast.newInstanceofExpression();
        instanceofExpression.setLeftOperand((Expression) ctx.expression().accept(this));
        instanceofExpression.setRightOperand((Type) ctx.typeType().accept(this));
        return instanceofExpression;
    }

    @Override
    public ConditionalExpression visitTernaryExpr(JavaParser.TernaryExprContext ctx) {
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
        } else if (ctx.CHAR_LITERAL() != null) {
            CharacterLiteral characterLiteral = ast.newCharacterLiteral();
            characterLiteral.setEscapedValue(ctx.CHAR_LITERAL().getText());
            return characterLiteral;
        } else if (ctx.NULL_LITERAL() != null) {
            return ast.newNullLiteral();
        } else if (ctx.STRING_LITERAL() != null) {
            StringLiteral stringLiteral = ast.newStringLiteral();
            stringLiteral.setEscapedValue(ctx.STRING_LITERAL().getText());
            return stringLiteral;
        } else if (ctx.integerLiteral() != null) {
            return ast.newNumberLiteral(ctx.integerLiteral().getText());
        } else { // float literal
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
    public Type visitTypeTypeOrVoid(JavaParser.TypeTypeOrVoidContext ctx) {
        if (ctx.VOID() != null) {
            return ast.newPrimitiveType(PrimitiveType.VOID);
        } else {
            return (Type) ctx.typeType().accept(this);
        }
    }

    public AnnotationTypeDeclaration visitAnnotationTypeDeclaration(JavaParser.AnnotationTypeDeclarationContext ctx) {
        AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
        annotationTypeDeclaration.setName(getIdentifier(ctx.IDENTIFIER()));
        annotationTypeDeclaration.bodyDeclarations().addAll(createList(ctx.annotationTypeBody().annotationTypeElementDeclaration()));
        return annotationTypeDeclaration;
    }

    @Override
    public BodyDeclaration visitAnnotationTypeElementDeclaration(JavaParser.AnnotationTypeElementDeclarationContext ctx) {
        BodyDeclaration bodyDeclaration = (BodyDeclaration) ctx.annotationTypeElementRest().accept(this);
        bodyDeclaration.modifiers().addAll(createList(ctx.modifier()));
        return bodyDeclaration;
    }

    @Override
    public ASTNode visitAnnotationTypeElementRest(JavaParser.AnnotationTypeElementRestContext ctx) {
        if (ctx.typeType() != null) {
            if (ctx.annotationMethodOrConstantRest().annotationMethodRest() != null) {
                AnnotationTypeMemberDeclaration annotationTypeMemberDeclaration = ast.newAnnotationTypeMemberDeclaration();
                annotationTypeMemberDeclaration.setType((Type) ctx.typeType().accept(this));
                annotationTypeMemberDeclaration.setName(getIdentifier(ctx.annotationMethodOrConstantRest().annotationMethodRest().IDENTIFIER()));
                if (ctx.annotationMethodOrConstantRest().annotationMethodRest().defaultValue() != null) {
                    annotationTypeMemberDeclaration.setDefault((Expression) ctx.annotationMethodOrConstantRest().annotationMethodRest().defaultValue().accept(this));
                }
                return annotationTypeMemberDeclaration;
            } else {
                JavaParser.AnnotationConstantRestContext annotationConstantRestContext = ctx.annotationMethodOrConstantRest().annotationConstantRest();
                List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(annotationConstantRestContext.variableDeclarators());
                FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
                fieldDeclaration.setType((Type) ctx.typeType().accept(this));
                fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
                return fieldDeclaration;
            }
        } else {
            return ctx.getChild(0).accept(this);
        }
    }

    private List<SingleVariableDeclaration> getFormalParameters(JavaParser.FormalParametersContext ctx) {
        if (ctx.formalParameterList() == null) {
            return emptyList();
        }

        final List<SingleVariableDeclaration> list = new ArrayList<>();
        if (ctx.formalParameterList().getChild(0) instanceof JavaParser.FormalParameterContext) {
            createList(ctx.formalParameterList().formalParameter()).forEach(formalParameter -> list.add((SingleVariableDeclaration) formalParameter));
            if (ctx.formalParameterList().lastFormalParameter() != null) {
                SingleVariableDeclaration formalParameter = (SingleVariableDeclaration) ctx.formalParameterList().lastFormalParameter().accept(this);
                list.add(formalParameter);
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
        singleVariableDeclaration.setName(getIdentifier(ctx.variableDeclaratorId().IDENTIFIER()));
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
        if (ctx.thenBranch != null) {
            Statement thenBranch = (Statement) ctx.thenBranch.accept(this);
            if (thenBranch != null) ifStatement.setThenStatement(thenBranch);
        }
        if (ctx.elseBranch != null) {
            Statement elseBranch = (Statement) ctx.elseBranch.accept(this);
            if (elseBranch != null) ifStatement.setElseStatement(elseBranch);
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
            List<Dimension> dimensions = getDimensions(ctx.forControl().enhancedForControl().variableDeclaratorId().dimensions());
            singleVariableDeclaration.extraDimensions().addAll(dimensions);

            forStatement.setParameter(singleVariableDeclaration);

            forStatement.setBody((Statement) ctx.statement().accept(this));
            return forStatement;
        } else {
            ForStatement forStatement = ast.newForStatement();

            if (ctx.forControl().forInit() != null) {
                if (ctx.forControl().forInit().localVariableDeclaration() != null) {
                    JavaParser.LocalVariableDeclarationContext localVariableDeclarationContext = ctx.forControl().forInit().localVariableDeclaration();
                    List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(localVariableDeclarationContext.variableDeclarators());
                    VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragments.get(0));
                    variableDeclarationExpression.fragments().addAll(fragments.subList(1, fragments.size()));
                    variableDeclarationExpression.modifiers().addAll(createList(localVariableDeclarationContext.variableModifier()));
                    variableDeclarationExpression.setType((Type) localVariableDeclarationContext.typeType().accept(this));

                    forStatement.initializers().add(variableDeclarationExpression);
                } else {
                    forStatement.initializers().addAll(createList(ctx.forControl().forInit().expressionList().expression()));
                }
            }

            if (ctx.forControl().expression() != null) {
                forStatement.setExpression((Expression) ctx.forControl().expression().accept(this));
            }

            if (ctx.forControl().forUpdate != null) {
                forStatement.updaters().addAll(createList(ctx.forControl().forUpdate.expression()));
            }

            Statement statement = (Statement) ctx.statement().accept(this);
            if (statement != null) forStatement.setBody(statement);

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
    public ASTNode visitEmptyStmt(JavaParser.EmptyStmtContext ctx) {
        return ast.newEmptyStatement();
    }

    @Override
    public ASTNode visitDoStmt(JavaParser.DoStmtContext ctx) {
        DoStatement doStatement = ast.newDoStatement();
        doStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));
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
        // TODO: do we need to convert dimensions as well?
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
        List<Type> types = createList(ctx.qualifiedName()).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList());
        if (types.size() == 1) {
            return types.get(0);
        }
        UnionType unionType = ast.newUnionType();
        unionType.types().addAll(types);
        return unionType;
    }

    @Override
    public SwitchStatement visitSwitchStmt(JavaParser.SwitchStmtContext ctx) {
        SwitchStatement switchStatement = ast.newSwitchStatement();
        switchStatement.setExpression((Expression) ctx.parExpression().expression().accept(this));

        List<Statement> statements = ctx.switchBlockStatementGroup().stream().flatMap(stmt -> getSwitchBlockStatements(stmt).stream()).collect(toList());
        switchStatement.statements().addAll(statements);
        switchStatement.statements().addAll(createList(ctx.switchLabel()));
        return switchStatement;
    }

    @Override
    public SwitchCase visitSwitchLabel(JavaParser.SwitchLabelContext ctx) {
        SwitchCase switchCase = ast.newSwitchCase();
        switchCase.setExpression(null); // default case
        if (ctx.expression() != null) {
            switchCase.setExpression((Expression) ctx.constantExpression.accept(this));
        } else if (ctx.enumConstantName != null) {
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
        return ast.newExpressionStatement((Expression) ctx.expression().accept(this));
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
        } else if (ctx.THIS() != null) {
            ThisExpression thisExpression = ast.newThisExpression();
            ASTNode qualifier = ctx.expression().accept(this);
            if (qualifier instanceof FieldAccess) {
                List<SimpleName> names = new ArrayList<>();
                toQualifiedName((FieldAccess) qualifier, names);
                QualifiedName qualifiedName = ast.newQualifiedName(names.get(0), names.get(1));
                for (int i = 2; i < names.size(); i++) {
                    qualifiedName = ast.newQualifiedName(qualifiedName, names.get(i));
                }
                thisExpression.setQualifier(qualifiedName);
            } else {
                thisExpression.setQualifier((Name) ctx.expression().accept(this));
            }
            return thisExpression;
        } else if (ctx.methodCall() != null) {
            Expression expression = (Expression) ctx.expression().accept(this);
            // This is a hack: when parsing primary, if it is 'super' it is not clear if it's a super field access
            // or super method invocation. We use this check here to distinguish between them
            if (expression instanceof SuperFieldAccess) {
                if (((SuperFieldAccess) expression).getName().toString().equals("MISSING")) {
                    SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
                    superMethodInvocation.setName(getIdentifier(ctx.methodCall().IDENTIFIER()));
                    if (ctx.methodCall().expressionList() != null) {
                        superMethodInvocation.arguments().addAll(createList(ctx.methodCall().expressionList().expression()));
                    }
                    return superMethodInvocation;
                }
            }
            MethodInvocation methodInvocation = (MethodInvocation) ctx.methodCall().accept(this);
            methodInvocation.setExpression(expression);
            return methodInvocation;
        } else if (ctx.SUPER() != null) {
            SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
            superMethodInvocation.setQualifier((Name) ctx.expression().accept(this));
            superMethodInvocation.arguments().addAll(getArguments(ctx.superSuffix().arguments()));
            if (ctx.superSuffix().IDENTIFIER() != null) {
                superMethodInvocation.setName(ast.newSimpleName(ctx.superSuffix().IDENTIFIER().getText()));
            }
            return superMethodInvocation;
        } else if (ctx.NEW() != null) {
            ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();

            if (ctx.innerCreator().classCreatorRest().classBody() != null) {
                AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                anonymousClassDeclaration.bodyDeclarations().addAll(createList(ctx.innerCreator().classCreatorRest().classBody().classBodyDeclaration()));
                classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
            }


            Type type = ast.newSimpleType(getIdentifier(ctx.innerCreator().IDENTIFIER()));
            if (ctx.innerCreator().nonWildcardTypeArgumentsOrDiamond() != null) {
                List<ASTNode> typeParameters = createList(ctx.innerCreator().nonWildcardTypeArgumentsOrDiamond().nonWildcardTypeArguments().typeList().typeType());
                type = ast.newParameterizedType(type);
                ((ParameterizedType) type).typeArguments().addAll(typeParameters);
            }

            classInstanceCreation.setExpression((Expression) ctx.expression().accept(this));
            classInstanceCreation.setType(type);
            classInstanceCreation.arguments().addAll(getArguments(ctx.innerCreator().classCreatorRest().arguments()));
            if (ctx.nonWildcardTypeArguments() != null) {
                classInstanceCreation.typeArguments().addAll(createList(ctx.nonWildcardTypeArguments().typeList().typeType()));
            }

            return classInstanceCreation;
        }
        else if (ctx.explicitGenericInvocation() != null) {
            JavaParser.ExplicitGenericInvocationSuffixContext suffixContext = ctx.explicitGenericInvocation().explicitGenericInvocationSuffix();
            if (suffixContext.SUPER() != null) {
                SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
                superMethodInvocation.typeArguments().addAll(createList(ctx.explicitGenericInvocation().nonWildcardTypeArguments().typeList().typeType()));
                if (suffixContext.IDENTIFIER() != null) {
                    superMethodInvocation.setName(getIdentifier(suffixContext.IDENTIFIER()));
                }
                superMethodInvocation.arguments().addAll(getArguments(suffixContext.arguments()));
                return superMethodInvocation;
            } else {
                Expression expression = (Expression) ctx.expression().accept(this);
                SimpleName name = getIdentifier(suffixContext.IDENTIFIER());
                List<ASTNode> typeArguments = createList(ctx.explicitGenericInvocation().nonWildcardTypeArguments().typeList().typeType());
                List<Expression> arguments = getArguments(suffixContext.arguments());

                // The same hack as before
                if (expression instanceof SuperFieldAccess) {
                    SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
                    superMethodInvocation.setName(name);
                    superMethodInvocation.typeArguments().addAll(typeArguments);
                    superMethodInvocation.arguments().addAll(arguments);
                    return superMethodInvocation;
                }

                MethodInvocation methodInvocation = ast.newMethodInvocation();
                methodInvocation.setExpression(expression);
                methodInvocation.setName(name);
                methodInvocation.typeArguments().addAll(typeArguments);
                methodInvocation.arguments().addAll(arguments);
                return methodInvocation;
            }
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

    @Override
    public ASTNode visitModifier(JavaParser.ModifierContext ctx) {
        if (ctx.classOrInterfaceModifier() != null) {
            return ctx.classOrInterfaceModifier().accept(this);
        }
        return ast.newModifier(Modifier.ModifierKeyword.toKeyword(ctx.getText()));
    }

    @Override
    public ASTNode visitClassOrInterfaceModifier(JavaParser.ClassOrInterfaceModifierContext ctx) {
        if (ctx.annotation() != null) {
            return ctx.annotation().accept(this);
        }
        return ast.newModifier(Modifier.ModifierKeyword.toKeyword(ctx.getText()));
    }

    @Override
    public ASTNode visitVariableModifier(JavaParser.VariableModifierContext ctx) {
        if (ctx.annotation() != null) {
            return ctx.annotation().accept(this);
        }
        return ast.newModifier(Modifier.ModifierKeyword.FINAL_KEYWORD);
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

    private int getDimensionsSize(JavaParser.DimensionsContext ctx) {
        if (ctx == null || ctx.children == null) {
            return 0;
        }
        return ctx.children.size() / 2;
    }

    private List<Dimension> getDimensions(JavaParser.DimensionsContext ctx) {
        if (ctx == null) return emptyList();
        return IntStream.range(0, getDimensionsSize(ctx)).mapToObj(i -> ast.newDimension()).collect(toList());
    }

    private SimpleName getIdentifier(TerminalNode node) {
        return ast.newSimpleName(node.getText());
    }

    private List<VariableDeclarationFragment> getVariableDeclarationFragments(JavaParser.VariableDeclaratorsContext ctx) {
        List<VariableDeclarationFragment> fragments = new ArrayList<>();
        for (JavaParser.VariableDeclaratorContext variableDeclaratorContext : ctx.variableDeclarator()) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(getIdentifier(variableDeclaratorContext.variableDeclaratorId().IDENTIFIER()));
            fragment.setInitializer((Expression) variableDeclaratorContext.accept(this));
            fragment.extraDimensions().addAll(getDimensions(variableDeclaratorContext.variableDeclaratorId().dimensions()));
            fragments.add(fragment);
        }
        return fragments;
    }

    private List<VariableDeclarationFragment> getVariableDeclarationFragments(List<JavaParser.ConstantDeclaratorContext> constantDeclaratorContexts) {
        List<VariableDeclarationFragment> fragments = new ArrayList<>();
        for (JavaParser.ConstantDeclaratorContext constantDeclaratorContext : constantDeclaratorContexts) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(ast.newSimpleName(constantDeclaratorContext.IDENTIFIER().getText()));
            fragment.setInitializer((Expression) constantDeclaratorContext.accept(this));
            fragment.extraDimensions().addAll(getDimensions(constantDeclaratorContext.dimensions()));
            fragments.add(fragment);
        }
        return fragments;
    }

    private List<Expression> getArguments(JavaParser.ArgumentsContext arguments) {
        if (arguments.expressionList() == null) {
            return emptyList();
        }
        return createList(arguments.expressionList().expression(), Expression.class);
    }

    private void toQualifiedName(FieldAccess fieldAccess, List<SimpleName> names) {
        Expression expression = fieldAccess.getExpression();
        if (expression instanceof SimpleName) {
            names.add(ast.newSimpleName(((SimpleName) expression).getIdentifier()));
        } else {
            toQualifiedName((FieldAccess) expression, names);
        }
        names.add(ast.newSimpleName(fieldAccess.getName().getIdentifier()));
    }

    private List<Statement> getSwitchBlockStatements(JavaParser.SwitchBlockStatementGroupContext ctx) {
        List<Statement> switchLabels = createList(ctx.switchLabel(), Statement.class);
        List<Statement> blockStatements = createList(ctx.blockStatement(), Statement.class);
        List<Statement> result = new ArrayList<>(switchLabels);
        result.addAll(blockStatements);
        return result;
    }
}
