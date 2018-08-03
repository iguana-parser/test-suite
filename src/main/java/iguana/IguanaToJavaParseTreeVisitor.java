package iguana;

import iguana.utils.collections.key.IntKey2;
import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.Block;
import org.iguana.grammar.symbol.*;
import org.iguana.parsetree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class IguanaToJavaParseTreeVisitor implements ParseTreeVisitor<Object> {

    private AST ast = AST.newAST(AST.JLS10);
    private Input input;

    public IguanaToJavaParseTreeVisitor(Input input) {
        this.input = input;
    }

    @Override
    public Object visitNonterminalNode(NonterminalNode node) {
        switch (node.getName()) {

            case "CompilationUnit":
                return visitCompilationUnit(node);

            case "PackageDeclaration":
                return visitPackageDeclaration(node);

            case "ImportDeclaration":
                return visitImportDeclaration(node);

            case "NormalClassDeclaration":
                return visitNormalClassDeclaration(node);

            case "EnumDeclaration":
                return visitEnumDeclaration(node);

            case "EnumConstant":
                return visitEnumConstant(node);

            case "NormalInterfaceDeclaration":
                return visitNormalInterfaceDeclaration(node);

            case "AbstractMethodDeclaration":
                return visitAbstractMethodDeclaration(node);

            case "AnnotationTypeDeclaration":
                return visitAnnotationTypeDeclaration(node);

            case "ConstantDeclaration":
                return visitConstantDeclaration(node);

            case "AnnotationMethodDeclaration":
                return visitAnnotationMethodDeclaration(node);

            case "Annotation":
                return visitAnnotation(node);

            case "ElementValueArrayInitializer":
                return visitElementValueArrayInitializer(node);

            case "ElementValuePair":
                return visitElementValuePair(node);

            case "FieldDeclaration":
                return visitFieldDeclaration(node);

            case "VariableDeclarator":
                return visitVariableDeclarator(node);

            case "MethodDeclaration":
                return visitMethodDeclaration(node);

            case "Throws":
                return visitThrows(node);

            case "FormalParameterList":
                return visitFormalParameterList(node);

            case "FormalParameter":
                return visitFormalParameter(node);

            case "LastFormalParameter":
                return visitLastFormalParameter(node);

            case "Result":
                return visitResult(node);

            case "Block":
                return visitBlock(node);

            case "LocalVariableDeclarationStatement":
                return visitLocalVariableDeclarationStatement(node);

            case "Statement":
                return visitStatement(node);

            case "ForInit":
                return visitForInit(node);

            case "Resource":
                return visitResource(node);

            case "SwitchLabel":
                return visitSwitchLabel(node);

            case "CatchClause":
                return visitCatchClause(node);

            case "CatchType":
                return visitCatchType(node);

            case "Finally":
                return visitFinally(node);

            case "Expression":
                return visitExpression(node);

            case "ClassInstanceCreationExpression":
                return visitClassInstanceCreationExpression(node);

            case "TypeDeclSpecifier":
                return visitTypeDeclSpecifier(node);

            case "TypeArgumentsOrDiamond":
                return visitTypeArgumentsOrDiamond(node);

            case "TypeArguments":
                return visitTypeArguments(node);

            case "ClassBody":
                return visitClassBody(node);

            case "ArrayCreationExpression":
                return visitArrayCreationExpression(node);

            case "ReferenceTypeNonArrayType":
                return visitReferenceTypeNonArrayType(node);

            case "ArrayInitializer":
                return visitArrayInitializer(node);

            case "Primary":
                return visitPrimary(node);

            case "SuperSuffix":
                return visitSuperSuffix(node);

            case "MethodInvocation":
                return visitMethodInvocation(node);

            case "Literal":
                return visitLiteral(node);

            case "ConstructorDeclaration":
                return visitConstructorDeclaration(node);

            case "ConstructorBody":
                return visitConstructorBody(node);

            case "BlockStatement":
                return visitBlockStatement(node);

            case "ExplicitConstructorInvocation":
                return visitExplicitConstructorInvocation(node);

            case "Initializer":
                return visitInitializer(node);

            case "QualifiedIdentifier":
                return visitQualifiedIdentifier(node);

            case "ArrayType":
                return visitArrayType(node);

            case "TypeArgument":
                return visitTypeArgument(node);

            case "TypeParameter":
                return visitTypeParameter(node);

            case "TypeBound":
                return visitTypeBound(node);

            case "VariableModifier":
            case "FieldModifier":
            case "MethodModifier":
            case "InterfaceModifier":
            case "ConstantModifier":
            case "AbstractMethodModifier":
            case "ConstructorModifier":
            case "ClassModifier":
                return visitModifier(node);

            case "PrimitiveType":
                return visitPrimitiveType(node);

            case "Identifier":
                return visitIdentifier(node);
        }

        return visitChildren(node);
    }

    private Name visitIdentifier(NonterminalNode node) {
        return ast.newSimpleName(node.childAt(0).getText());
    }

    private PrimitiveType visitPrimitiveType(NonterminalNode node) {
        return ast.newPrimitiveType(PrimitiveType.toCode(node.getText()));
    }

    private Object visitModifier(NonterminalNode node) {
        if (node.hasChild("Annotation")) {
            return node.childAt(0).accept(this);
        } else {
            return ast.newModifier(Modifier.ModifierKeyword.toKeyword(node.getText()));
        }
    }

    // TypeBound: "extends" {ReferenceType "&"}+
    private Object visitTypeBound(NonterminalNode node) {
        return node.childAt(1).accept(this);
    }

    // Identifier TypeBound?
    private Object visitTypeParameter(NonterminalNode node) {
        TypeParameter typeParameter = ast.newTypeParameter();

        typeParameter.setName((SimpleName) node.childAt(0).accept(this));

        List<Type> typeBounds = (List<Type>) node.childAt(1).accept(this);
        if (typeBounds != null) {
            typeParameter.typeBounds().addAll(typeBounds);
        }

        return typeParameter;
    }

    private Object visitTypeArgument(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {
            //  Type
            case "simpleTypeArgument": {
                return node.childAt(0).accept(this);
            }

            //  "?" (("extends" | "super") Type)?
            case "wildCardTypeArgument": {
                WildcardType wildcardType = ast.newWildcardType();
                Type type = (Type) node.childAt(1).accept(this);
                if (type != null) {
                    String superOrExtends = node.childAt(1).childAt(0).childAt(0).getText();
                    if (superOrExtends.equals("super")) {
                        wildcardType.setUpperBound(false);
                    }
                    wildcardType.setBound(type);
                }
                return wildcardType;
            }

            default:
                throw new RuntimeException("Unkown TypeArugment: " + node);
        }
    }

    // (ReferenceTypeNonArrayType | PrimaryType) ("[" "]")+
    private Type visitArrayType(NonterminalNode node) {
        Type type = (Type) node.childAt(0).accept(this);
        int dimensions = getDimensionsSize(node.childAt(1));
        return ast.newArrayType(type, dimensions);
    }

    // {Identifier "."}+
    private Name visitQualifiedIdentifier(NonterminalNode node) {
        List<SimpleName> identifiers = (List<SimpleName>) node.childAt(0).accept(this);

        if (identifiers.size() == 1) {
            return identifiers.get(0);
        }

        Name qualifier = identifiers.get(0);
        SimpleName simpleName = identifiers.get(1);
        qualifier = ast.newQualifiedName(qualifier, simpleName);

        for (int i = 2; i < identifiers.size(); i++) {
            simpleName = identifiers.get(i);
            qualifier = ast.newQualifiedName(qualifier, simpleName);
        }

        return qualifier;
    }

    // "static"? Block
    private Object visitInitializer(NonterminalNode node) {
        Initializer initializer = ast.newInitializer();
        if (node.childAt(0).children().size() > 0) {
            initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
        }
        initializer.setBody((Block) node.childAt(1).accept(this));
        return initializer;
    }

    private Object visitExplicitConstructorInvocation(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {

            // NonWildTypeArguments? "this" Arguments ";"
            case "constructorInvocation": {
                ConstructorInvocation constructorInvocation = ast.newConstructorInvocation();
                List<Type> typeArguments = (List<Type>) node.childAt(0).accept(this);
                if (typeArguments != null) {
                    constructorInvocation.typeArguments().addAll(typeArguments);
                }

                constructorInvocation.arguments().addAll((List<Expression>) node.childAt(2).accept(this));
                return constructorInvocation;
            }

            // (Primary ".")? NonWildTypeArguments? "super" Arguments ";"
            case "superConstructorInvocation": {
                SuperConstructorInvocation superConstructorInvocation = ast.newSuperConstructorInvocation();
                List<Type> typeArguments = (List<Type>) node.childAt(2).accept(this);
                if (typeArguments != null) {
                    superConstructorInvocation.typeArguments().addAll(typeArguments);
                }

                Expression expression = (Expression) node.childAt(0).accept(this);
                if (expression != null) {
                    superConstructorInvocation.setExpression(expression);
                }

                superConstructorInvocation.arguments().addAll((List<Expression>) node.childAt(3).accept(this));
                return superConstructorInvocation;
            }

            default:
                throw new RuntimeException("Unknown constructor type: " + node);
        }
    }

    private Object visitBlockStatement(NonterminalNode node) {
        Object result = node.childAt(0).accept(this);
        // TODO: change it by introducing a nonterminal LocalTypeDeclaration
        if (result instanceof TypeDeclaration) {
            return ast.newTypeDeclarationStatement((TypeDeclaration) result);
        }
        return result;
    }

    // "{" ExplicitConstructorInvocation? BlockStatement* "}"
    private Block visitConstructorBody(NonterminalNode node) {
        Block block = ast.newBlock();
        Statement explicitConstructorInvocation = (Statement) node.childAt(1).accept(this);
        List<Statement> statements = (List<Statement>) node.childAt(2).accept(this);
        List<Statement> body = new ArrayList<>(statements.size() + 1);
        if (explicitConstructorInvocation != null) {
            body.add(explicitConstructorInvocation);
        }
        body.addAll(statements);
        block.statements().addAll(body);
        return block;
    }

    // ConstructorModifier* TypeParameters? Identifier "(" FormalParameterList? ")" Throws? ConstructorBody
    private MethodDeclaration visitConstructorDeclaration(NonterminalNode node) {
        MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
        constructorDeclaration.setConstructor(true);
        constructorDeclaration.setReturnType2(null);
        constructorDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));

        List<TypeParameter> typeParameters = (List<TypeParameter>) node.childAt(1).accept(this);
        if (typeParameters != null) {
            constructorDeclaration.typeParameters().addAll(typeParameters);
        }
        constructorDeclaration.setName((SimpleName) node.childAt(2).accept(this));

        List<SingleVariableDeclaration> formalParameters = (List<SingleVariableDeclaration>) node.childAt(4).accept(this);
        if (formalParameters != null) {
            constructorDeclaration.parameters().addAll(formalParameters);
        }

        List<Type> exceptionTypes = (List<Type>) node.childAt(6).accept(this);
        if (exceptionTypes != null) {
            constructorDeclaration.thrownExceptionTypes().addAll(exceptionTypes);
        }

        constructorDeclaration.setBody((Block) node.childAt(7).accept(this));

        return constructorDeclaration;
    }

    private Expression visitLiteral(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {
            case "integerLiteral":
            case "floatLiteral": {
                return ast.newNumberLiteral(node.getText());
            }

            case "booleanLiteral": {
                return ast.newBooleanLiteral(Boolean.parseBoolean(node.getText()));
            }

            case "characterLiteral": {
                CharacterLiteral characterLiteral = ast.newCharacterLiteral();
                characterLiteral.setEscapedValue(node.getText());
                return characterLiteral;
            }

            case "stringLiteral": {
                StringLiteral stringLiteral = ast.newStringLiteral();
                stringLiteral.setEscapedValue(node.getText());
                return stringLiteral;
            }

            case "nullLiteral": {
                return ast.newNullLiteral();
            }

            default:
                throw new RuntimeException("Unknown Literal: " + node);
        }
    }

    // Identifier Arguments
    private MethodInvocation visitMethodInvocation(NonterminalNode node) {
        MethodInvocation methodInvocation = ast.newMethodInvocation();
        methodInvocation.setName((SimpleName) node.childAt(0).accept(this));
        methodInvocation.arguments().addAll((List<Expression>) node.childAt(1).accept(this));
        return methodInvocation;
    }

    // "." NonWildTypeArguments? Identifier Arguments?
    private Expression visitSuperSuffix(NonterminalNode node) {
        List<Expression> arguments = (List<Expression>) node.childAt(3).accept(this);
        if (arguments != null) {
            SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
            superMethodInvocation.setName(getIdentifier(node.childAt(2)));
            superMethodInvocation.arguments().addAll(arguments);
            return superMethodInvocation;
        } else {
            // "." NonWildTypeArguments? Identifier Arguments?
            SuperFieldAccess superFieldAccess = ast.newSuperFieldAccess();
            superFieldAccess.setName(getIdentifier(node.childAt(2)));
            return superFieldAccess;
        }
    }

    private Expression visitPrimary(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {

            case "literalPrimary": {
                return (Expression) node.childAt(0).accept(this);
            }

            // (QualifiedIdentifier ".")? "this"
            case "thisPrimary": {
                ThisExpression thisExpression = ast.newThisExpression();
                Name qualifiedIdentifier = (Name) node.childAt(0).accept(this);
                if (qualifiedIdentifier != null) {
                    thisExpression.setQualifier(qualifiedIdentifier);
                }
                return thisExpression;
            }

            case "superPrimary": {
                return (Expression) node.childAt(1).accept(this);
            }

            case "idPrimary": {
                return getIdentifier(node.childAt(0));
            }

            case "typeLiteralPrimary": {
                TypeLiteral typeLiteral = ast.newTypeLiteral();
                Type type = (Type) node.childAt(0).accept(this);
                if (type == null) {
                    type = ast.newPrimitiveType(PrimitiveType.VOID);
                }
                typeLiteral.setType(type);
                return typeLiteral;
            }

            // "(" Expression ")"
            case "parExprPrimary": {
                ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                parenthesizedExpression.setExpression((Expression) node.childAt(1).accept(this));
                return parenthesizedExpression;
            }

            default:
                throw new RuntimeException("Unexpected primary " + node);
        }
    }

    // "{"  {VariableInitializer ","}* ","? "}"
    private ArrayInitializer visitArrayInitializer(NonterminalNode node) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll((List<Expression>) node.childAt(1).accept(this));
        return arrayInitializer;
    }

    // TypeDeclSpecifier TypeArguments?
    private Type visitReferenceTypeNonArrayType(NonterminalNode node) {
        Type type = (Type) node.childAt(0).accept(this);
        List<Type> typeArguments = (List<Type>) node.childAt(1).accept(this);
        if (typeArguments != null) {
            type = ast.newParameterizedType(type);
            ((ParameterizedType) type).typeArguments().addAll(typeArguments);
        }
        return type;
    }

    /*
     * ArrayCreationExpression
     *   = (PrimitiveType | ReferenceType) ("[" Expression "]")+ ("[" "]")*
     *   | (PrimitiveType | ReferenceTypeNonArrayType) ("[" "]")+ ArrayInitializer
     */
    private ArrayCreation visitArrayCreationExpression(NonterminalNode node) {
        ArrayCreation arrayCreation = ast.newArrayCreation();
        if (node.hasChild("ArrayInitializer")) { // Second alternative
            int dimensions = getDimensionsSize(node.childAt(1));
            arrayCreation.setType(ast.newArrayType((Type) node.childAt(0).accept(this), dimensions));
            arrayCreation.setInitializer((ArrayInitializer) node.childAt(2).accept(this));
        } else {
            List<Expression> expressions = (List<Expression>) node.childAt(1).accept(this);
            int dimensions = getDimensionsSize(node.childAt(2)) + expressions.size();
            arrayCreation.setType(ast.newArrayType((Type) node.childAt(0).accept(this), dimensions));
            arrayCreation.dimensions().addAll(expressions);
        }
        return arrayCreation;
    }

    // "{" ClassBodyDeclaration* "}"
    private List<BodyDeclaration> visitClassBody(NonterminalNode node) {
        return (List<BodyDeclaration>) node.childAt(1).accept(this);
    }

    // '<' {TypeArgument ","}+ '>'
    private Object visitTypeArguments(NonterminalNode node) {
        return node.childAt(1).accept(this);
    }

    // TypeArgumentsOrDiamond
    //        = "<" ">"
    //        | TypeArguments
    private Object visitTypeArgumentsOrDiamond(NonterminalNode node) {
        if (node.childAt(0).getName().equals("TypeArguments")) {
            return node.childAt(0).accept(this);
        }
        return emptyList();
    }

    // TypeDeclSpecifier: Identifier (TypeArguments? "." Identifier)*
    private Type visitTypeDeclSpecifier(NonterminalNode node) {
        Type type = ast.newSimpleType((SimpleName) node.childAt(0).accept(this));

        List<Object> rest = (List<Object>) node.childAt(1).accept(this);

        for (int i = 0; i < rest.size(); i++) {
            Object n = rest.get(i);
            if (n instanceof List) { // TypeArguments
                type = ast.newParameterizedType(type);
                ((ParameterizedType) type).typeArguments().addAll((List<Type>) n);
            } else { // Identifier
                type = ast.newQualifiedType(type, (SimpleName) n);
            }
        }

        return type;
    }

    // TypeArguments? TypeDeclSpecifier TypeArgumentsOrDiamond? Arguments ClassBody?
    private ClassInstanceCreation visitClassInstanceCreationExpression(NonterminalNode node) {
        ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();

        List<Type> typeArguments = (List<Type>) node.childAt(0).accept(this);
        if (typeArguments != null) {
            classInstanceCreation.typeArguments().addAll(typeArguments);
        }

        Type type = (Type) node.childAt(1).accept(this);
        List<Type> typeArgumentsOrDiamond = (List<Type>) node.childAt(2).accept(this);
        if (typeArgumentsOrDiamond != null) {
            type = ast.newParameterizedType(type);
            ((ParameterizedType) type).typeArguments().addAll(typeArgumentsOrDiamond);
        }

        classInstanceCreation.setType(type);
        classInstanceCreation.arguments().addAll((List<Expression>) node.childAt(3).accept(this));

        List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.childAt(4).accept(this);
        if (bodyDeclarations != null) {
            AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
            anonymousClassDeclaration.bodyDeclarations().addAll(bodyDeclarations);
            classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
        }
        return classInstanceCreation;
    }

    private Expression visitExpression(NonterminalNode node) {
        // Infix expression
        if (node.getGrammarDefinition().getLabel() == null || node.getGrammarDefinition().getLabel().equals("comparisonExpr")) {
            InfixExpression infixExpression = ast.newInfixExpression();
            infixExpression.setLeftOperand((Expression) node.childAt(0).accept(this));
            infixExpression.setRightOperand((Expression) node.childAt(2).accept(this));
            infixExpression.setOperator(InfixExpression.Operator.toOperator(node.childAt(1).getText()));
            return infixExpression;
        }

        switch (node.getGrammarDefinition().getLabel()) {

            case "fieldAccess":
                return visitFieldAccess(node);

            case "methodCall":
                return visitMethodCall(node);

            case "arrayAccess":
                return visitArrayAccess(node);

            case "postfix":
                return visitPostfix(node);

            case "prefix":
                return visitPrefix(node);

            case "newClass":
                return visitNewClass(node);

            case "primitiveCastExpr":
                return visitPrimitiveCastExpr(node);

            case "castExpr":
                return visitCastExpression(node);

            case "instanceOfExpr":
                return visitInstanceOfExpr(node);

            case "conditionalExpr":
                return visitConditionalExpr(node);

            case "assignmentExpr":
                return visitAssignmentExpr(node);

            case "primaryExpr":
                return visitPrimaryExpr(node);

            default:
                throw new RuntimeException("Unexpected exception type");
        }
    }

    private Expression visitPrimaryExpr(NonterminalNode node) {
        return (Expression) node.childAt(0).accept(this);
    }

    // Expression AssignmentOperator Expression
    private Assignment visitAssignmentExpr(NonterminalNode node) {
        Assignment assignment = ast.newAssignment();
        assignment.setLeftHandSide((Expression) node.childAt(0).accept(this));
        assignment.setRightHandSide((Expression) node.childAt(2).accept(this));
        assignment.setOperator(Assignment.Operator.toOperator(node.childAt(1).getText()));
        return assignment;
    }

    // Expression "?" Expression ":" Expression
    private ConditionalExpression visitConditionalExpr(NonterminalNode node) {
        ConditionalExpression conditionalExpression = ast.newConditionalExpression();
        conditionalExpression.setExpression((Expression) node.childAt(0).accept(this));
        conditionalExpression.setThenExpression((Expression) node.childAt(2).accept(this));
        conditionalExpression.setElseExpression((Expression) node.childAt(4).accept(this));
        return conditionalExpression;
    }

    // Expression "instanceof" Type
    private InstanceofExpression visitInstanceOfExpr(NonterminalNode node) {
        InstanceofExpression instanceofExpression = ast.newInstanceofExpression();
        instanceofExpression.setLeftOperand((Expression) node.childAt(0).accept(this));
        instanceofExpression.setRightOperand((Type) node.childAt(2).accept(this));
        return instanceofExpression;
    }

    // "(" ReferenceType ")" Expression
    private CastExpression visitCastExpression(NonterminalNode node) {
        CastExpression castExpression = ast.newCastExpression();
        castExpression.setType((Type) node.childAt(1).accept(this));
        castExpression.setExpression((Expression) node.childAt(3).accept(this));
        return castExpression;
    }

    // "(" Type ")" Expression
    private CastExpression visitPrimitiveCastExpr(NonterminalNode node) {
        CastExpression castExpression = ast.newCastExpression();
        castExpression.setType((Type) node.childAt(1).accept(this));
        castExpression.setExpression((Expression) node.childAt(3).accept(this));
        return castExpression;
    }

    // "new" (ClassInstanceCreationExpression | ArrayCreationExpression)
    private Expression visitNewClass(NonterminalNode node) {
        return (Expression) node.childAt(1).accept(this);
    }

    // ("+" !>> "+" | "-" !>> "-" | "++" | "--" | "!" | "~") Expression
    private Expression visitPrefix(NonterminalNode node) {
        PrefixExpression prefixExpression = ast.newPrefixExpression();
        prefixExpression.setOperand((Expression) node.childAt(1).accept(this));
        prefixExpression.setOperator(PrefixExpression.Operator.toOperator(node.childAt(0).getText()));
        return prefixExpression;
    }

    // Expression ("++" | "--")
    private PostfixExpression visitPostfix(NonterminalNode node) {
        PostfixExpression postfixExpression = ast.newPostfixExpression();
        postfixExpression.setOperand((Expression) node.childAt(0).accept(this));
        postfixExpression.setOperator(PostfixExpression.Operator.toOperator(node.childAt(1).getText()));
        return postfixExpression;
    }

    // Expression "[" Expression "]"
    private ArrayAccess visitArrayAccess(NonterminalNode node) {
        ArrayAccess arrayAccess = ast.newArrayAccess();
        arrayAccess.setArray((Expression) node.childAt(0).accept(this));
        arrayAccess.setIndex((Expression) node.childAt(2).accept(this));
        return arrayAccess;
    }

    // MethodInvocation
    private Expression visitMethodCall(NonterminalNode node) {
        return (Expression) node.childAt(0).accept(this);
    }

    // Expression "." Selector
    private Expression visitFieldAccess(NonterminalNode node) {
        Expression expression = (Expression) node.childAt(0).accept(this);
        return getFieldAccess(expression, (NonterminalNode) node.childAt(2));
    }

    private Statement visitStatement(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {

            case "blockStmt":
                return visitBlockStmt(node);

            case "emptyStmt":
                return visitEmptyStmt(node);

            case "expressionStmt":
                return visitExpressionStmt(node);

            case "assertStmt":
                return visitAssertStmt(node);

            case "switchStmt":
                return visitSwitchStmt(node);

            case "doStmt":
                return visitDoStmt(node);

            case "breakStmt":
                return visitBreakStmt(node);

            case "continueStmt":
                return visitContinueStmt(node);

            case "returnStmt":
                return visitReturnStatement(node);

            case "synchronizedStmt":
                return visitSynchronizedStmt(node);

            case "throwStmt":
                return visitThrowStmt(node);

            case "tryStmt":
                return visitTryStmt(node);

            case "tryFinally":
                return visitTryFinally(node);

            case "tryWithResourcesStmt":
                return visitTryWithResourcesStmt(node);

            case "labelStmt":
                return visitLabelStmt(node);

            case "ifStmt":
                return visitIfStmt(node);

            case "ifElseStmt":
                return visitIfElseStmt(node);

            case "whileStmt":
                return visitWhileStmt(node);

            case "forStmt":
                return visitForStmt(node);

            default:
                throw new RuntimeException("Unknown statement: " + node);
        }
    }

    // Statement: "finally" Block
    private Object visitFinally(NonterminalNode node) {
        return node.childAt(1).accept(this);
    }

    // {QualifiedIdentifier "|"}+
    private Type visitCatchType(NonterminalNode node) {
        List<Type> types = getQualifiedTypes(node.childAt(0));
        if (types.size() == 1) {
            return types.get(0);
        }
        UnionType unionType = ast.newUnionType();
        unionType.types().addAll(types);
        return unionType;
    }

    private List<Type> getQualifiedTypes(ParseTreeNode node) {
        List<Name> qualifiedIdentifiers = (List<Name>) node.accept(this);

        List<Type> types = new ArrayList<>(qualifiedIdentifiers.size());
        for (int i = 0; i < qualifiedIdentifiers.size(); i++) {
            Name name = qualifiedIdentifiers.get(i);
            types.add(ast.newSimpleType(name));
        }
        return types;
    }

    // CatchClause: "catch" "(" VariableModifier* CatchType Identifier ")" Block
    private Object visitCatchClause(NonterminalNode node) {
        CatchClause catchClause = ast.newCatchClause();

        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.modifiers().addAll(getModifiers(node.childAt(2)));
        singleVariableDeclaration.setType((Type) node.childAt(3).accept(this));
        singleVariableDeclaration.setName((SimpleName) node.childAt(4).accept(this));
        catchClause.setException(singleVariableDeclaration);

        catchClause.setBody((Block) node.childAt(6).accept(this));

        return catchClause;
    }

    /*
     * SwitchLabel
     *   : "case" Expression ":"
     *   | "default" ":"
     */
    private SwitchCase visitSwitchLabel(NonterminalNode node) {
        SwitchCase switchCase = ast.newSwitchCase();
        switchCase.setExpression(null); // default case
        if (node.children().size() == 3) {
            switchCase.setExpression((Expression) node.childAt(1).accept(this));
        }
        return switchCase;
    }

    // Resource: VariableModifier* ReferenceType VariableDeclaratorId "=" Expression
    private VariableDeclarationExpression visitResource(NonterminalNode node) {
        VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
        variableDeclarationFragment.setName(getIdentifier(node.childAt(2).childAt(0)));
        variableDeclarationFragment.setInitializer((Expression) node.childAt(4).accept(this));

        VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(variableDeclarationFragment);
        variableDeclarationExpression.modifiers().addAll(getModifiers(node.childAt(0)));
        variableDeclarationExpression.setType((Type) node.childAt(1).accept(this));
        return variableDeclarationExpression;
    }

    private Object visitForInit(NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {
            // {Expression ","}+
            case "expressions": {
                return node.childAt(0).accept(this);
            }

            // VariableModifier* Type {VariableDeclarator ","}+
            case "variableDecl": {
                List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
                VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragments.get(0));
                variableDeclarationExpression.fragments().addAll(fragments.subList(1, fragments.size()));
                variableDeclarationExpression.setType((Type) node.childAt(1).accept(this));
                variableDeclarationExpression.modifiers().addAll(getModifiers(node.childAt(0)));
                return singletonList(variableDeclarationExpression);
            }

            default:
                throw new RuntimeException("Should not reach here");
        }
    }

    // Statement: "for" "(" ForControl ")" Statement
    private Statement visitForStmt(NonterminalNode node) {
        ParseTreeNode forControlNode = node.childAt(2);

        switch (((Rule) forControlNode.getGrammarDefinition()).getLabel()) {

            // ForInit? ";" Expression? ";" ForUpdate?
            case "traditionalFor": {
                ForStatement forStatement = ast.newForStatement();

                List<Expression> forInit = (List<Expression>) forControlNode.childAt(0).accept(this);
                if (forInit != null) {
                    forStatement.initializers().addAll(forInit);
                }

                Expression expression = (Expression) forControlNode.childAt(2).accept(this);
                if (expression != null) {
                    forStatement.setExpression(expression);
                }

                List<Expression> forUpdate = (List<Expression>) forControlNode.childAt(4).accept(this);
                if (forUpdate != null) {
                    forStatement.updaters().addAll(forUpdate);
                }

                Statement statement = (Statement) node.childAt(4).accept(this);
                forStatement.setBody(statement);

                return forStatement;
            }

            // FormalParameter ":" Expression
            case "enhancedFor": {
                EnhancedForStatement forStatement = ast.newEnhancedForStatement();

                forStatement.setExpression((Expression) forControlNode.childAt(2).accept(this));
                SingleVariableDeclaration singleVariableDeclaration = (SingleVariableDeclaration) forControlNode.childAt(0).accept(this);
                forStatement.setParameter(singleVariableDeclaration);

                forStatement.setBody((Statement) node.childAt(4).accept(this));
                return forStatement;
            }

            default:
                throw new RuntimeException("Should not reach here");
        }
    }

    // Statement: "while" "(" Expression ")" Statement
    private WhileStatement visitWhileStmt(NonterminalNode node) {
        WhileStatement whileStatement = ast.newWhileStatement();
        whileStatement.setExpression((Expression) node.childAt(2).accept(this));

        Statement body = (Statement) node.childAt(4).accept(this);
        whileStatement.setBody(body);
        return whileStatement;
    }

    // Statement: "if" "(" Expression ")" Statement "else" Statement
    private IfStatement visitIfElseStmt(NonterminalNode node) {
        IfStatement ifStatement = ast.newIfStatement();
        ifStatement.setExpression((Expression) node.childAt(2).accept(this));
        Statement thenBranch = (Statement) node.childAt(4).accept(this);
        if (thenBranch != null)
            ifStatement.setThenStatement(thenBranch);
        Statement elseBranch = (Statement) node.childAt(6).accept(this);
        if (elseBranch != null)
            ifStatement.setElseStatement(elseBranch);
        return ifStatement;
    }

    // Statement: "if" "(" Expression ")" Statement !>>> "else"
    private IfStatement visitIfStmt(NonterminalNode node) {
        IfStatement ifStatement = ast.newIfStatement();
        ifStatement.setExpression((Expression) node.childAt(2).accept(this));
        Statement thenBranch = (Statement) node.childAt(4).accept(this);
        if (thenBranch != null) ifStatement.setThenStatement(thenBranch);
        return ifStatement;
    }

    // Statement: Identifier ":" Statement
    private LabeledStatement visitLabelStmt(NonterminalNode node) {
        LabeledStatement labeledStatement = ast.newLabeledStatement();
        labeledStatement.setLabel((SimpleName) node.childAt(0).accept(this));
        labeledStatement.setBody((Statement) node.childAt(2).accept(this));
        return labeledStatement;
    }

    // Statement: "try" ResourceSpecification Block CatchClause* Finally?
    private TryStatement visitTryWithResourcesStmt(NonterminalNode node) {
        TryStatement tryStatement = ast.newTryStatement();
        tryStatement.resources().addAll((List<Expression>) node.childAt(1).accept(this));
        tryStatement.setBody((Block) node.childAt(2).accept(this));
        tryStatement.catchClauses().addAll((List<CatchClause>) node.childAt(3).accept(this));

        Block block = (Block) node.childAt(4).accept(this);
        if (block != null) {
            tryStatement.setFinally(block);
        }
        return tryStatement;
    }

    // Statement: "try" Block CatchClause* Finally
    private TryStatement visitTryFinally(NonterminalNode node) {
        TryStatement tryStatement = ast.newTryStatement();
        tryStatement.setBody((Block) node.childAt(1).accept(this));
        tryStatement.catchClauses().addAll((List<CatchClause>) node.childAt(2).accept(this));
        tryStatement.setFinally((Block) node.childAt(3).accept(this));
        return tryStatement;
    }

    // Statement: "try" Block CatchClause+
    private TryStatement visitTryStmt(NonterminalNode node) {
        TryStatement tryStatement = ast.newTryStatement();
        tryStatement.setBody((Block) node.childAt(1).accept(this));
        tryStatement.catchClauses().addAll((List<CatchClause>) node.childAt(2).accept(this));
        return tryStatement;
    }

    // Statement: "throw" Expression ";"
    private ThrowStatement visitThrowStmt(NonterminalNode node) {
        ThrowStatement throwStatement = ast.newThrowStatement();
        throwStatement.setExpression((Expression) node.childAt(1).accept(this));
        return throwStatement;
    }

    // SynchronizedStatement: "synchronized" "(" Expression ")" Block
    private SynchronizedStatement visitSynchronizedStmt(NonterminalNode node) {
        SynchronizedStatement synchronizedStatement = ast.newSynchronizedStatement();
        synchronizedStatement.setExpression((Expression) node.childAt(2).accept(this));
        synchronizedStatement.setBody((Block) node.childAt(4).accept(this));
        return synchronizedStatement;
    }

    // Statement: "return" Expression? ";"
    private ReturnStatement visitReturnStatement(NonterminalNode node) {
        ReturnStatement returnStatement = ast.newReturnStatement();
        Expression expression = (Expression) node.childAt(1).accept(this);
        if (expression != null) {
            returnStatement.setExpression(expression);
        }
        return returnStatement;
    }

    // Statement: "continue" Identifier? ";"
    private ContinueStatement visitContinueStmt(NonterminalNode node) {
        ContinueStatement continueStatement = ast.newContinueStatement();
        SimpleName identifier = (SimpleName) node.childAt(1).accept(this);
        if (identifier != null) {
            continueStatement.setLabel(identifier);
        }
        return continueStatement;
    }

    // Statement: "break" Identifier? ";"
    private BreakStatement visitBreakStmt(NonterminalNode node) {
        BreakStatement breakStatement = ast.newBreakStatement();

        SimpleName identifier = (SimpleName) node.childAt(1).accept(this);
        if (identifier != null) {
            breakStatement.setLabel(identifier);
        }
        return breakStatement;
    }

    // Statement: "do" Statement "while" "(" Expression ")" ";"
    private DoStatement visitDoStmt(NonterminalNode node) {
        DoStatement doStatement = ast.newDoStatement();
        doStatement.setBody((Statement) node.childAt(1).accept(this));
        doStatement.setExpression((Expression) node.childAt(4).accept(this));
        return doStatement;
    }

    // Statement: "switch" "(" Expression ")" "{" SwitchBlockStatementGroup* SwitchLabel* "}"
    private SwitchStatement visitSwitchStmt(NonterminalNode node) {
        SwitchStatement switchStatement = ast.newSwitchStatement();
        switchStatement.setExpression((Expression) node.childAt(2).accept(this));

        List<Statement> statements = node.childAt(5).children().stream().flatMap(stmt -> getSwitchBlockStatements(stmt).stream()).collect(toList());
        switchStatement.statements().addAll(statements);
        switchStatement.statements().addAll((List<Statement>) node.childAt(6).accept(this));
        return switchStatement;
    }

    // Statement: "assert" Expression (":" Expression)? ";"
    private AssertStatement visitAssertStmt(NonterminalNode node) {
        AssertStatement assertStatement = ast.newAssertStatement();
        assertStatement.setExpression((Expression) node.childAt(1).accept(this));

        Expression message = (Expression) node.childAt(2).accept(this);
        if (message != null) {
            assertStatement.setMessage(message);
        }
        return assertStatement;
    }

    // Statement: Expression ";"
    private ExpressionStatement visitExpressionStmt(NonterminalNode node) {
        return ast.newExpressionStatement((Expression) node.childAt(0).accept(this));
    }

    // Statement: ;
    private EmptyStatement visitEmptyStmt(NonterminalNode node) {
        return ast.newEmptyStatement();
    }

    // Statement: Block
    private Block visitBlockStmt(NonterminalNode node) {
        return (Block) node.childAt(0).accept(this);
    }

    // LocalVariableDeclarationStatement: VariableModifier* Type {VariableDeclarator ","}+ ";"
    private VariableDeclarationStatement visitLocalVariableDeclarationStatement(NonterminalNode node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
        VariableDeclarationStatement variableDeclarationStatement = ast.newVariableDeclarationStatement(fragments.get(0));
        for (int i = 1; i < fragments.size(); i++) {
            variableDeclarationStatement.fragments().add(fragments.get(i));
        }
        variableDeclarationStatement.setType((Type) node.childAt(1).accept(this));
        variableDeclarationStatement.modifiers().addAll(getModifiers(node.childAt(0)));
        return variableDeclarationStatement;
    }

    //  "{" BlockStatement* "}"
    private Block visitBlock(NonterminalNode node) {
        Block block = ast.newBlock();
        List<ASTNode> blockStatements = (List<ASTNode>) node.childAt(1).accept(this);
        for (int i = 0; i < blockStatements.size(); i++) {
            ASTNode blockStatement = blockStatements.get(i);
            if (blockStatement instanceof TypeDeclaration) {
                block.statements().add(ast.newTypeDeclarationStatement((TypeDeclaration) blockStatement));
            } else {
                block.statements().add(blockStatement);
            }
        }
        return block;
    }

    //   Type
    // | "void"
    private Type visitResult(NonterminalNode node) {
        if (node.childAt(0).getName().equals("Type")) {
            return (Type) node.childAt(0).accept(this);
        }
        return ast.newPrimitiveType(PrimitiveType.VOID);
    }

    /*
     * LastFormalParameter
     *   : VariableModifier* Type "..." VariableDeclaratorId
     *   | FormalParameter
     */
    private SingleVariableDeclaration visitLastFormalParameter(NonterminalNode node) {
        if (node.children().size() == 1) { // Second alternative
            return (SingleVariableDeclaration) node.childAt(0).accept(this);
        }
        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.setType((Type) node.childAt(1).accept(this));
        singleVariableDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        singleVariableDeclaration.setVarargs(true);
        singleVariableDeclaration.setName((SimpleName) node.childAt(3).childAt(0).accept(this));
        return singleVariableDeclaration;
    }

    // FormalParameter: VariableModifier* Type VariableDeclaratorId
    private SingleVariableDeclaration visitFormalParameter(NonterminalNode node) {
        SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
        singleVariableDeclaration.setType((Type) node.childAt(1).accept(this));
        singleVariableDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        ParseTreeNode variableDeclaratorId = node.childAt(2);
        singleVariableDeclaration.extraDimensions().addAll(getDimensions(variableDeclaratorId.childAt(1)));
        singleVariableDeclaration.setName((SimpleName) variableDeclaratorId.childAt(0).accept(this));
        return singleVariableDeclaration;
    }

    // FormalParameterList: (FormalParameter ",")* LastFormalParameter
    private List<SingleVariableDeclaration> visitFormalParameterList(NonterminalNode node) {
        List<SingleVariableDeclaration> formalParameters = (List<SingleVariableDeclaration>) node.childAt(0).accept(this);
        SingleVariableDeclaration lastFormalParameter = (SingleVariableDeclaration) node.childAt(1).accept(this);
        formalParameters.add(lastFormalParameter);
        return formalParameters;
    }

    // Throws: "throws" {QualifiedIdentifier ","}+
    private Object visitThrows(NonterminalNode node) {
        return getQualifiedTypes(node.childAt(1));
    }

    // MethodDeclaration: MethodModifier* TypeParameters? Result MethodDeclarator Throws? MethodBody
    // MethodDeclarator:  Identifier "(" FormalParameterList? ")" ("[" "]")*
    private MethodDeclaration visitMethodDeclaration(NonterminalNode node) {
        MethodDeclaration methodDeclaration = ast.newMethodDeclaration();

        methodDeclaration.modifiers().addAll((List<IExtendedModifier>) node.childAt(0).accept(this));

        List<Type> typeParameters = (List<Type>) node.childAt(1).accept(this);
        if (typeParameters != null) {
            methodDeclaration.typeParameters().addAll(typeParameters);
        }

        methodDeclaration.setReturnType2((Type) node.childAt(2).accept(this));

        ParseTreeNode methodDeclarator = node.childAt(3);
        methodDeclaration.setName((SimpleName) methodDeclarator.childAt(0).accept(this));

        List<SingleVariableDeclaration> parameters = (List<SingleVariableDeclaration>) methodDeclarator.childAt(2).accept(this);
        if (parameters != null) {
            methodDeclaration.parameters().addAll(parameters);
        }

        methodDeclaration.extraDimensions().addAll(getDimensions(methodDeclarator.childAt(4)));

        List<Type> exceptionTypes = (List<Type>) node.childAt(4).accept(this);
        if (exceptionTypes != null) {
            methodDeclaration.thrownExceptionTypes().addAll(exceptionTypes);
        }

        methodDeclaration.setBody((Block) node.childAt(5).accept(this));

        return methodDeclaration;
    }

    // EnumConstant: Annotation* Identifier Arguments? ClassBody?
    private EnumConstantDeclaration visitEnumConstant(NonterminalNode node) {
        EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

        enumConstantDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        enumConstantDeclaration.setName(getIdentifier(node.childAt(1)));

        List<Expression> arguments = (List<Expression>) node.childAt(2).accept(this);
        if (arguments != null) {
            enumConstantDeclaration.arguments().addAll(arguments);
        }

        List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.childAt(3).accept(this);
        if (bodyDeclarations != null) {
            AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
            anonymousClassDeclaration.bodyDeclarations().addAll(bodyDeclarations);
            enumConstantDeclaration.setAnonymousClassDeclaration(anonymousClassDeclaration);
        }

        return enumConstantDeclaration;
    }

    // NormalClassDeclaration: ClassModifier* "class" Identifier TypeParameters? ("extends" Type)? ("implements" TypeList)? ClassBody;
    private TypeDeclaration visitNormalClassDeclaration(NonterminalNode node) {
        TypeDeclaration classDeclaration = ast.newTypeDeclaration();
        classDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        classDeclaration.setName((SimpleName) node.childAt(2).accept(this));

        List<Type> typeParameters = (List<Type>) node.childAt(3).accept(this);
        if (typeParameters != null) {
            classDeclaration.typeParameters().addAll(typeParameters);
        }

        Type type = (Type) node.childAt(4).accept(this);
        if (type != null) { // ("extends" Type)?
            classDeclaration.setSuperclassType(type);
        }

        List<Type> superInterfaces = (List<Type>) node.childAt(5).accept(this);
        if (superInterfaces != null) {
            classDeclaration.superInterfaceTypes().addAll(superInterfaces);
        }

        classDeclaration.bodyDeclarations().addAll((List<BodyDeclaration>) node.childAt(6).accept(this));

        return classDeclaration;
    }

    // CompilationUnit: PackageDeclaration? ImportDeclaration* TypeDeclaration*
    private CompilationUnit visitCompilationUnit(NonterminalNode node) {
        CompilationUnit compilationUnit = ast.newCompilationUnit();
        PackageDeclaration packageDeclaration = (PackageDeclaration) node.childAt(0).accept(this);
        if (packageDeclaration != null) {
            compilationUnit.setPackage(packageDeclaration);
        }
        compilationUnit.imports().addAll((List<ImportDeclaration>) node.childAt(1).accept(this));
        compilationUnit.types().addAll((List<TypeDeclaration>) node.childAt(2).accept(this));
        return compilationUnit;
    }

    // PackageDeclaration: Annotation* "package"  QualifiedIdentifier ";"
    private PackageDeclaration visitPackageDeclaration(NonterminalNode node) {
        PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
        packageDeclaration.annotations().addAll(getModifiers(node.childAt(0)));
        packageDeclaration.setName((Name) node.childAt(2).accept(this));
        return packageDeclaration;
    }

    // ImportDeclaration: "import"  "static"?  QualifiedIdentifier ("." "*")? ";"
    private ImportDeclaration visitImportDeclaration(NonterminalNode node) {
        ImportDeclaration importDeclaration = ast.newImportDeclaration();
        if (node.childAt(1).children().size() > 0) { // "static"?
            importDeclaration.setStatic(true);
        }
        importDeclaration.setName((Name) node.childAt(2).accept(this));
        if (node.childAt(3).children().size() > 0) { // ("." "*")?
            importDeclaration.setOnDemand(true);
        }
        return importDeclaration;
    }

    // EnumDeclaration: ClassModifier* "enum" Identifier ("implements" TypeList)? EnumBody
    private Object visitEnumDeclaration(NonterminalNode node) {
        EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
        enumDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        enumDeclaration.setName((SimpleName) node.childAt(2).accept(this));

        List<Type> typeList = (List<Type>) node.childAt(3).accept(this);
        if (typeList != null) {
            enumDeclaration.superInterfaceTypes().addAll(typeList);
        }

        ParseTreeNode enumBodyNode = node.childAt(4);
        // "{" {EnumConstant ","}* ","? EnumBodyDeclarations? "}"
        List<BodyDeclaration> enumConstants = (List<BodyDeclaration>) enumBodyNode.childAt(1).accept(this);
        enumDeclaration.enumConstants().addAll(enumConstants);

        List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) enumBodyNode.childAt(3).accept(this);
        if (bodyDeclarations != null) {
            enumDeclaration.bodyDeclarations().addAll(bodyDeclarations);
        }

        return enumDeclaration;
    }

    // NormalInterfaceDeclaration: InterfaceModifier* "interface" Identifier TypeParameters? ("extends" TypeList)? InterfaceBody
    private Object visitNormalInterfaceDeclaration(NonterminalNode node) {
        TypeDeclaration interfaceDeclaration = ast.newTypeDeclaration();
        interfaceDeclaration.setInterface(true);
        interfaceDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        interfaceDeclaration.setName((SimpleName) node.childAt(2).accept(this));

        List<TypeParameter> typeParameters = (List<TypeParameter>) node.childAt(3).accept(this);
        if (typeParameters != null) {
            interfaceDeclaration.typeParameters().addAll(typeParameters);
        }

        List<Type> superInterfaces = (List<Type>) node.childAt(4).accept(this);
        if (superInterfaces != null) {
            interfaceDeclaration.superInterfaceTypes().addAll(superInterfaces);
        }

        List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.childAt(5).accept(this);
        interfaceDeclaration.bodyDeclarations().addAll(bodyDeclarations);

        return interfaceDeclaration;
    }

    // AbstractMethodDeclaration: AbstractMethodModifier* TypeParameters? Result MethodDeclarator Throws? ";"
    private MethodDeclaration visitAbstractMethodDeclaration(NonterminalNode node) {
        MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
        methodDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));

        List<TypeParameter> typeParameters = (List<TypeParameter>) node.childAt(1).accept(this);
        if (typeParameters != null) {
            methodDeclaration.typeParameters().addAll(typeParameters);
        }

        Type returnType = (Type) node.childAt(2).accept(this);
        methodDeclaration.setReturnType2(returnType);

        // Identifier "(" FormalParameterList? ")" ("[" "]")*
        ParseTreeNode methodDeclarator = node.childAt(3);
        methodDeclaration.setName((SimpleName) methodDeclarator.childAt(0).accept(this));

        List<SingleVariableDeclaration> parameters = (List<SingleVariableDeclaration>) methodDeclarator.childAt(2).accept(this);
        if (parameters != null) {
            methodDeclaration.parameters().addAll(parameters);
        }

        methodDeclaration.extraDimensions().addAll(getDimensions(methodDeclarator.childAt(4)));

        List<Type> exceptionTypes = (List<Type>) node.childAt(4).accept(this);
        if (exceptionTypes != null) {
            methodDeclaration.thrownExceptionTypes().addAll(exceptionTypes);
        }

        return methodDeclaration;
    }

    // AnnotationTypeDeclaration: InterfaceModifier* "@" "interface" Identifier AnnotationTypeBody
    private AnnotationTypeDeclaration visitAnnotationTypeDeclaration(NonterminalNode node) {
        AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
        annotationTypeDeclaration.setName(getIdentifier(node.childAt(3)));
        annotationTypeDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));

        annotationTypeDeclaration.bodyDeclarations().addAll((List<BodyDeclaration>) node.childAt(4).accept(this));
        return annotationTypeDeclaration;
    }

    // ConstantDeclaration: ConstantModifier* Type {VariableDeclarator ","}+ ";"
    private FieldDeclaration visitConstantDeclaration(NonterminalNode node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
        FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
        fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
        fieldDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        fieldDeclaration.setType((Type) node.childAt(1).accept(this));
        return fieldDeclaration;
    }

    // AnnotationMethodDeclaration: AbstractMethodModifier* Type Identifier "(" ")" ("[" "]")* DefaultValue? ";"
    private AnnotationTypeMemberDeclaration visitAnnotationMethodDeclaration(NonterminalNode node) {
        AnnotationTypeMemberDeclaration annotationTypeMemberDeclaration = ast.newAnnotationTypeMemberDeclaration();
        annotationTypeMemberDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        annotationTypeMemberDeclaration.setType((Type) node.childAt(1).accept(this));
        annotationTypeMemberDeclaration.setName(getIdentifier(node.childAt(2)));
        Expression expression = (Expression) node.childAt(6).accept(this);
        annotationTypeMemberDeclaration.setDefault(expression);
        return annotationTypeMemberDeclaration;
    }

    // Annotation: "@" QualifiedIdentifier Values?
    private Annotation visitAnnotation(NonterminalNode node) {
        Name name = (Name) node.childAt(1).accept(this);

        if (node.childAt(2).children().size() == 0) {
            MarkerAnnotation markerAnnotation = ast.newMarkerAnnotation();
            markerAnnotation.setTypeName(name);
            return markerAnnotation;
        }

        List<Object> values = (List<Object>) node.childAt(2).accept(this);

        if (values == null) {
            values = emptyList();
        }

        if (values.size() == 1 && values.get(0) instanceof Expression) {
            SingleMemberAnnotation singleMemberAnnotation = ast.newSingleMemberAnnotation();
            singleMemberAnnotation.setTypeName(name);
            singleMemberAnnotation.setValue((Expression) values.get(0));
            return singleMemberAnnotation;
        }

        NormalAnnotation normalAnnotation = ast.newNormalAnnotation();
        normalAnnotation.setTypeName(name);
        normalAnnotation.values().addAll(values);
        return normalAnnotation;
    }

    // ArrayInitializer: ElementValueArrayInitializer: "{" { ElementValue "," }* ","? "}"
    private ArrayInitializer visitElementValueArrayInitializer(NonterminalNode node) {
        ArrayInitializer arrayInitializer = ast.newArrayInitializer();
        arrayInitializer.expressions().addAll((List<Expression>) node.childAt(1).accept(this));
        return arrayInitializer;
    }

    // ElementValuePair: Identifier "=" ElementValue
    private MemberValuePair visitElementValuePair(NonterminalNode node) {
        MemberValuePair memberValuePair = ast.newMemberValuePair();
        memberValuePair.setName(getIdentifier(node.childAt(0)));
        memberValuePair.setValue((Expression) node.childAt(2).accept(this));
        return memberValuePair;
    }

    // FieldDeclaration: FieldModifier* Type {VariableDeclarator ","}+ ";"
    private FieldDeclaration visitFieldDeclaration(NonterminalNode node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
        FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
        fieldDeclaration.modifiers().addAll(getModifiers(node.childAt(0)));
        fieldDeclaration.setType((Type) node.childAt(1).accept(this));
        fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
        return fieldDeclaration;
    }

    // VariableDeclarator: VariableDeclaratorId ("=" VariableInitializer)?
    private VariableDeclarationFragment visitVariableDeclarator(NonterminalNode node) {
        VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
        ParseTreeNode variableDeclaratorIdNode = node.childAt(0);
        fragment.setName((SimpleName) variableDeclaratorIdNode.childAt(0).accept(this));
        fragment.extraDimensions().addAll(getDimensions(variableDeclaratorIdNode.childAt(1)));

        Expression expression = (Expression) node.childAt(1).accept(this);
        if (expression != null) {
            fragment.setInitializer(expression);
        }
        return fragment;
    }

    private Object visitChildren(ParseTreeNode node) {
        int size = node.children().size();

        if (size == 1) {
            return node.childAt(0).accept(this);
        }

        List<Object> result = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            ParseTreeNode child = node.childAt(i);
            Object childResult = child.accept(this);
            if (childResult != null) {
                result.add(childResult);
            }
        }

        if (result.isEmpty()) {
            return null;
        }

        if (result.size() == 1) {
            return result.get(0);
        }

        return result;
    }

    @Override
    public ASTNode visitAmbiguityNode(AmbiguityNode node) {
        throw new RuntimeException("Ambiguity");
    }

    @Override
    public ASTNode visitTerminalNode(TerminalNode terminalNode) {
        return null;
    }

    @Override
    public Object visitMetaSymbolNode(MetaSymbolNode node) {
        Symbol definition = node.getGrammarDefinition();

        // Flatten sequence inside star and plus
        if (shouldBeFlattened(definition)) {
            if (definition instanceof Opt) {
                if (node.children().size() == 0) {
                    return null;
                }
                List<Object> result = (List<Object>) node.childAt(0).accept(this);
                if (result.size() == 1) {
                    return result.get(0);
                }
                return result;
            } else {
                int size = node.children().size();
                List<Object> result = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    ParseTreeNode child = node.childAt(i);
                    result.addAll((List<Object>) child.accept(this));
                }
                return result;
            }
        }

        if (definition instanceof Star || definition instanceof Plus || definition instanceof Sequence) {
            List<Object> result = new ArrayList<>(node.children().size());
            for (int i = 0; i < node.children().size(); i++) {
                ParseTreeNode child = node.childAt(i);
                Object childResult = child.accept(this);
                if (childResult != null) {
                    result.add(child.accept(this));
                }
            }
            return result;
        } else if (definition instanceof Alt) {
            return node.childAt(0).accept(this);
        } else { // Opt
            if (node.children().size() == 0) {
                return null;
            }
            return node.childAt(0).accept(this);
        }
    }

    private static boolean shouldBeFlattened(Symbol symbol) {
        return (symbol instanceof Star || symbol instanceof Plus || symbol instanceof Opt) && getSymbol(symbol) instanceof Sequence;
    }

    private static Symbol getSymbol(Symbol symbol) {
        if (symbol instanceof Star) {
            return ((Star) symbol).getSymbol();
        } else if (symbol instanceof Plus) {
            return ((Plus) symbol).getSymbol();
        } else if (symbol instanceof Opt) {
            return ((Opt) symbol).getSymbol();
        } else throw new RuntimeException("Unsupported symbol " + symbol);
    }

    private SimpleName getIdentifier(ParseTreeNode node) {
        return ast.newSimpleName(node.getText());
    }

    private List<IExtendedModifier> getModifiers(ParseTreeNode node) {
        return (List<IExtendedModifier>) node.accept(this);
    }

    // SwitchLabel+ BlockStatement+
    private List<Statement> getSwitchBlockStatements(ParseTreeNode node) {
        List<Statement> statements = (List<Statement>) node.childAt(0).accept(this);
        statements.addAll((List<Statement>) node.childAt(1).accept(this));
        return statements;
    }

    // ('[' ']')*
    private int getDimensionsSize(ParseTreeNode node) {
        return node.children().size();
    }

    private List<Dimension> getDimensions(ParseTreeNode node) {
        if (node == null) return emptyList();

        int size = getDimensionsSize(node);
        List<Dimension> dimensions = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            dimensions.add(ast.newDimension());
        }
        return dimensions;
    }

    private Expression getFieldAccess(Expression expression, NonterminalNode node) {
        switch (node.getGrammarDefinition().getLabel()) {
            case "idSelector": {
                FieldAccess fieldAccess = ast.newFieldAccess();
                fieldAccess.setExpression(expression);
                fieldAccess.setName(getIdentifier(node.childAt(0)));
                return fieldAccess;
            }

            // NonWildTypeArguments? MethodInvocation
            case "methodSelector": {
                MethodInvocation methodInvocation = (MethodInvocation) node.childAt(1).accept(this);
                methodInvocation.setExpression(expression);
                List<Type> typeArguments = (List<Type>) node.childAt(0).accept(this);
                if (typeArguments != null) {
                    methodInvocation.typeArguments().addAll(typeArguments);
                }
                return methodInvocation;
            }

            case "thisSelector": {
                ThisExpression thisExpression = ast.newThisExpression();
                thisExpression.setQualifier((Name) expression);
                return thisExpression;
            }

            case "superSelector": {
                Expression result = (Expression) node.childAt(1).accept(this);
                if (result instanceof SuperFieldAccess) {
                    ((SuperFieldAccess) result).setQualifier((Name) expression);
                } else {
                    ((SuperMethodInvocation) result).setQualifier((Name) expression);
                }
                return result;
            }

            // "new" TypeArguments? Identifier TypeArgumentsOrDiamond? Arguments ClassBody?
            case "newSelector": {
                ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();

                List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.childAt(5).accept(this);
                if (bodyDeclarations != null) {
                    AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                    anonymousClassDeclaration.bodyDeclarations().addAll(bodyDeclarations);
                    classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
                }

                classInstanceCreation.setExpression(expression);
                Type type = ast.newSimpleType(getIdentifier(node.childAt(2)));

                List<Expression> arguments = (List<Expression>) node.childAt(4).accept(this);
                classInstanceCreation.arguments().addAll(arguments);

                List<Type> typeArguments = (List<Type>) node.childAt(1).accept(this);
                if (typeArguments != null) {
                    classInstanceCreation.typeArguments().addAll(typeArguments);
                }

                List<Type> typeArgumentsOrDiamond = (List<Type>) node.childAt(3).accept(this);
                if (typeArgumentsOrDiamond != null) {
                    type = ast.newParameterizedType(type);
                    ((ParameterizedType) type).typeArguments().addAll(typeArgumentsOrDiamond);
                }

                classInstanceCreation.setType(type);
                return classInstanceCreation;
            }

            default:
                throw new RuntimeException("Unknown Selector: " + node);
        }
    }
}
