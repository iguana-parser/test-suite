package iguana;


import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.*;
import org.iguana.parsetree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class IguanaToJavaParseTreeVisitor implements ParseTreeVisitor<ASTNode> {

    private AST ast = AST.newAST(AST.JLS10);
    private Input input;

    public IguanaToJavaParseTreeVisitor(Input input) {
        this.input = input;
    }

    @Override
    public ASTNode visit(NonterminalNode node) {
        switch (node.getName()) {
            case "CompilationUnit": {
                CompilationUnit compilationUnit = ast.newCompilationUnit();
                if (node.hasChild("PackageDeclaration?")) {
                    compilationUnit.setPackage((PackageDeclaration) node.getChildWithName("PackageDeclaration?").accept(this));
                }
                compilationUnit.imports().addAll(createList(node.getChildWithName("ImportDeclaration*").children()));
                compilationUnit.types().addAll(createList(node.getChildWithName("TypeDeclaration*").children()));
                return compilationUnit;
            }

            case "PackageDeclaration": {
                PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
                packageDeclaration.setName((Name) node.getChildWithName("QualifiedIdentifier").accept(this));
                return packageDeclaration;
            }

            // ImportDeclaration: "import"  "static"?  {Identifier "."}+ ("." "*")? ";"
            case "ImportDeclaration": {
                ImportDeclaration importDeclaration = ast.newImportDeclaration();
                if (isOptionNotEmpty(node.childAt(1))) {
                    importDeclaration.setStatic(true);
                }
                importDeclaration.setName(getQualifiedName(node.childAt(2)));
                if (isOptionNotEmpty(node.childAt(3))) {
                    importDeclaration.setOnDemand(true);
                }
                return importDeclaration;
            }

            // NormalClassDeclaration: ClassModifier* "class" Identifier TypeParameters? ("extends" Type)? ("implements" TypeList)? ClassBody;
            case "NormalClassDeclaration": {
                TypeDeclaration classDeclaration = ast.newTypeDeclaration();
                classDeclaration.modifiers().addAll(createList(node.getChildWithName("ClassModifier*").children()));
                classDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));

                if (isOptionNotEmpty(node.getChildWithName("TypeParameters?"))) {
                    classDeclaration.typeParameters().addAll(getTypeParameters(node.getChildWithName("TypeParameters?").childAt(0)));
                }
                classDeclaration.bodyDeclarations().addAll(createList(node.getChildWithName("ClassBody").childAt(1).children()));

                ParseTreeNode extendsNode = node.childAt(4);
                if (isOptionNotEmpty(extendsNode)) { // ("extends" Type)?
                    classDeclaration.setSuperclassType((Type) getOption(extendsNode).get(1).accept(this));
                }
                return classDeclaration;
            }

            // EnumDeclaration: ClassModifier* "enum" Identifier ("implements" TypeList)? EnumBody
            case "EnumDeclaration": {
                EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
                enumDeclaration.modifiers().addAll(createList(node.getChildWithName("ClassModifier*").children()));
                enumDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                if (isOptionNotEmpty(node.childAt(3))) {
                    enumDeclaration.superInterfaceTypes().addAll(getTypeList(getOption(node.childAt(3)).get(1)));
                }

                List<BodyDeclaration> enumConstants = createList(node.getChildWithName("EnumBody").childAt(1).getChildrenWithName("EnumConstant"), BodyDeclaration.class);
                enumDeclaration.enumConstants().addAll(enumConstants);

                if (isOptionNotEmpty(node.getChildWithName("EnumBody").childAt(3))) {
                    List<BodyDeclaration> enumBodyDeclararions = createList(node.childAt(4).childAt(3).childAt(0).childAt(1).children(), BodyDeclaration.class);
                    enumDeclaration.bodyDeclarations().addAll(enumBodyDeclararions);
                }

                return enumDeclaration;
            }

            // EnumConstant: Annotation* Identifier Arguments? ClassBody?
            case "EnumConstant": {
                EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

                enumConstantDeclaration.modifiers().addAll(createList(node.childAt(0).children()));
                enumConstantDeclaration.setName(getIdentifier(node.childAt(1)));

//                if (isOptionNotEmpty(node.childAt(2))) {
//                    enumConstantDeclaration.arguments().addAll(getArguments(ctx.arguments()));
//                }
//
//                if (isOptionNotEmpty(node.childAt(3))) {
//                    AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
//                    anonymousClassDeclaration.bodyDeclarations().addAll(createList(ctx.classBody().classBodyDeclaration()));
//                    enumConstantDeclaration.setAnonymousClassDeclaration(anonymousClassDeclaration);
//                }

                return enumConstantDeclaration;

            }

            case "NormalInterfaceDeclaration": {
                TypeDeclaration interfaceDeclaration = ast.newTypeDeclaration();
                interfaceDeclaration.setInterface(true);
                interfaceDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                interfaceDeclaration.modifiers().addAll(createList(node.getChildWithName("InterfaceModifier*").children()));
                return interfaceDeclaration;
            }

            // InterfaceModifier* "@" "interface" Identifier AnnotationTypeBody
            case "AnnotationTypeDeclaration": {
                AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
                annotationTypeDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                annotationTypeDeclaration.modifiers().addAll(createList(node.getChildWithName("InterfaceModifier*").children()));
                // TODO: add annotation type bodies
                return annotationTypeDeclaration;
            }

            // FieldModifier* Type {VariableDeclarator ","}+ ";"
            case "FieldDeclaration": {
                List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(node.childAt(2));
                FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
                fieldDeclaration.setType((Type) node.getChildWithName("Type").accept(this));
                fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
                return fieldDeclaration;
            }

            // MethodDeclaration: MethodModifier* TypeParameters? Result MethodDeclarator Throws? MethodBody
            // MethodDeclarator:  Identifier "(" FormalParameterList? ")" ("[" "]")*
            case "MethodDeclaration": {
                MethodDeclaration methodDeclaration = ast.newMethodDeclaration();

                methodDeclaration.modifiers().addAll(createList(node.getChildWithName("MethodModifier*").children()));

                if (isOptionNotEmpty(node.getChildWithName("TypeParameters?"))) {
                    methodDeclaration.typeParameters().addAll(getTypeParameters(node.getChildWithName("TypeParameters?").childAt(0)));
                }
                methodDeclaration.setReturnType2((Type) node.getChildWithName("Result").accept(this));

                ParseTreeNode methodDeclarator = node.getChildWithName("MethodDeclarator");
                methodDeclaration.setName(getIdentifier(methodDeclarator.getChildWithName("Identifier")));

                if (isOptionNotEmpty(methodDeclarator.getChildWithName("FormalParameterList?"))) {
                    methodDeclaration.parameters().addAll(getFormalParameters(methodDeclarator.getChildWithName("FormalParameterList?").childAt(0)));
                }
                methodDeclaration.extraDimensions().addAll(getDimensions(methodDeclarator.childAt(2)));

                if (isOptionNotEmpty(node.getChildWithName("Throws?"))) {
                    methodDeclaration.thrownExceptionTypes().addAll(getThrownExceptionTypes(node.getChildWithName("Throws?").childAt(0)));
                }

                methodDeclaration.setBody((Block) node.getChildWithName("MethodBody").accept(this));

                return methodDeclaration;
            }

            // FormalParameter: VariableModifier* Type VariableDeclaratorId
            case "FormalParameter": {
                SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
                singleVariableDeclaration.setType((Type) node.getChildWithName("Type").accept(this));
                singleVariableDeclaration.modifiers().addAll(createList(node.getChildWithName("VariableModifier*").children()));
                ParseTreeNode variableDeclaratorId = node.getChildWithName("VariableDeclaratorId");
                singleVariableDeclaration.extraDimensions().addAll(getDimensions(variableDeclaratorId.childAt(1)));
                singleVariableDeclaration.setName(getIdentifier(variableDeclaratorId.getChildWithName("Identifier")));
                return singleVariableDeclaration;
            }

            /*
             * LastFormalParameter
             *   : VariableModifier* Type "..." VariableDeclaratorId
             *   | FormalParameter
             */
            case "LastFormalParameter": {
                if (node.children().size() == 1) { // Second alternative
                    return node.childAt(0).accept(this);
                }
                SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
                singleVariableDeclaration.setType((Type) node.getChildWithName("Type").accept(this));
                singleVariableDeclaration.modifiers().addAll(createList(node.childAt(0).children()));
                singleVariableDeclaration.setVarargs(true);
                singleVariableDeclaration.setName(getIdentifier(node.getChildWithName("VariableDeclaratorId").getChildWithName("Identifier")));
                return singleVariableDeclaration;
            }

            case "Result": {
                if (node.childAt(0).getName().equals("Type")) {
                    return node.childAt(0).accept(this);
                }
                return ast.newPrimitiveType(PrimitiveType.VOID);
            }

            case "Block": {
                Block block = ast.newBlock();
                List<ASTNode> blockStatements = createList(node.getChildWithName("BlockStatement*").children());
                for (ASTNode blockStatement : blockStatements) {
                    if (blockStatement instanceof TypeDeclaration) {
                        block.statements().add(ast.newTypeDeclarationStatement((TypeDeclaration) blockStatement));
                    } else {
                        block.statements().add(blockStatement);
                    }
                }
                return block;
            }

            // LocalVariableDeclarationStatement: VariableModifier* Type {VariableDeclarator ","}+ ";"
            case "LocalVariableDeclarationStatement": {
                List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(node.childAt(2));
                VariableDeclarationStatement variableDeclarationStatement = ast.newVariableDeclarationStatement(fragments.get(0));
                for (int i = 1; i < fragments.size(); i++) {
                    variableDeclarationStatement.fragments().add(fragments.get(i));
                }
                variableDeclarationStatement.setType((Type) node.getChildWithName("Type").accept(this));
                variableDeclarationStatement.modifiers().addAll(createList(node.getChildWithName("VariableModifier*").children()));
                return variableDeclarationStatement;
            }

            case "Statement": {

                if (node.getGrammarDefinition().getLabel() == null) {
                    return node.childAt(0).accept(this);
                }

                switch (node.getGrammarDefinition().getLabel()) {

                    // Expression ";"
                    case "expressionStmt": {
                        return ast.newExpressionStatement((Expression) node.getChildWithName("Expression").accept(this));
                    }

                    // "assert" Expression (":" Expression)? ";"
                    case "assertStmt": {
                        AssertStatement assertStatement = ast.newAssertStatement();
                        assertStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        if (isOptionNotEmpty(node.childAt(2))) {
                            assertStatement.setMessage((Expression) node.childAt(2).childAt(0).getChildWithName("Expression").accept(this));
                        }
                        return assertStatement;
                    }

                    // "switch" "(" Expression ")" "{" SwitchBlockStatementGroup* SwitchLabel* "}"
                    case "switchStmt": {
                        SwitchStatement switchStatement = ast.newSwitchStatement();
                        switchStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));

                        List<Statement> statements = node.getChildWithName("SwitchBlockStatementGroup*").children().stream().flatMap(stmt -> getSwitchBlockStatements(stmt).stream()).collect(toList());
                        switchStatement.statements().addAll(statements);
                        switchStatement.statements().addAll(createList(node.getChildWithName("SwitchLabel*").children()));
                        return switchStatement;
                    }

                    // "do" Statement "while" "(" Expression ")" ";"
                    case "doStmt": {
                        DoStatement doStatement = ast.newDoStatement();
                        doStatement.setBody((Statement) node.getChildWithName("Statement").accept(this));
                        doStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        return doStatement;
                    }

                    // "break" Identifier? ";"
                    case "breakStmt": {
                        BreakStatement breakStatement = ast.newBreakStatement();
                        if (isOptionNotEmpty(node.getChildWithName("Identifier?"))) {
                            breakStatement.setLabel(getIdentifier(node.getChildWithName("Identifier?").childAt(0)));
                        }
                        return breakStatement;
                    }

                    // "continue" Identifier? ";"
                    case "continueStmt": {
                        ContinueStatement continueStatement = ast.newContinueStatement();
                        if (isOptionNotEmpty(node.getChildWithName("Identifier?"))) {
                            continueStatement.setLabel(getIdentifier(node.getChildWithName("Identifier?").childAt(0)));
                        }
                        return continueStatement;
                    }

                    // "return" Expression? ";"
                    case "returnStmt": {
                        ReturnStatement returnStatement = ast.newReturnStatement();
                        if (isOptionNotEmpty(node.getChildWithName("Expression?"))) {
                            returnStatement.setExpression((Expression) node.getChildWithName("Expression?").childAt(0).accept(this));
                        }
                        return returnStatement;
                    }

                    // synchronizedStmt: "synchronized" "(" Expression ")" Block
                    case "synchronizedStmt": {
                        SynchronizedStatement synchronizedStatement = ast.newSynchronizedStatement();
                        synchronizedStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        synchronizedStatement.setBody((Block) node.getChildWithName("Block").accept(this));
                        return synchronizedStatement;
                    }

                    // "throw" Expression ";"
                    case "throwStmt": {
                        ThrowStatement throwStatement = ast.newThrowStatement();
                        throwStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        return throwStatement;
                    }

                    // "try" Block (CatchClause+ | (CatchClause* Finally))
                    case "tryStmt": {
                        // TODO: complete after removing superfluous grouping
                        TryStatement tryStatement = ast.newTryStatement();
                        tryStatement.setBody((Block) node.getChildWithName("Block").accept(this));

//                        tryStatement.catchClauses().addAll(createList(ctx.catchClause()));
//
//                        if (ctx.finallyBlock() != null) {
//                            tryStatement.setFinally((Block) ctx.finallyBlock().accept(this));
//                        }
                        return tryStatement;
                    }

                    // "try" ResourceSpecification Block CatchClause* Finally?
                    case "tryWithResourcesStmt": {
                        TryStatement tryStatement = ast.newTryStatement();
                        tryStatement.resources().addAll(createList(node.childAt(1).childAt(0).getChildrenWithName("Resource")));
                        tryStatement.setBody((Block) node.getChildWithName("Block").accept(this));
                        tryStatement.catchClauses().addAll(createList(node.getChildWithName("CatchClause*").children()));

                        if (isOptionNotEmpty(node.getChildWithName("Finally?"))) {
                            tryStatement.setFinally((Block) node.getChildWithName("Finally?").childAt(0).accept(this));
                        }
                        return tryStatement;
                    }

                    // Identifier ":" Statement
                    case "labelStmt": {
                        LabeledStatement labeledStatement = ast.newLabeledStatement();
                        labeledStatement.setLabel(getIdentifier(node.getChildWithName("Identifier")));
                        labeledStatement.setBody((Statement) node.getChildWithName("Statement").accept(this));
                        return labeledStatement;
                    }

                    // "if" "(" Expression ")" Statement !>>> "else"
                    case "ifStmt": {
                        IfStatement ifStatement = ast.newIfStatement();
                        ifStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        Statement thenBranch = (Statement) node.getChildWithName("Statement").accept(this);
                        if (thenBranch != null) ifStatement.setThenStatement(thenBranch);
                        return ifStatement;
                    }

                    // "if" "(" Expression ")" Statement "else" Statement
                    // TODO: add labels for ifThenElse
                    case "ifElseStmt": {
                        IfStatement ifStatement = ast.newIfStatement();
                        ifStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
//                        Statement thenBranch = (Statement) node.getChildWithName("Statement").accept(this);
//                        if (thenBranch != null)
//                            ifStatement.setThenStatement(thenBranch);
//                        Statement elseBranch = (Statement) ctx.elseBranch.accept(this);
//                        if (elseBranch != null)
//                            ifStatement.setElseStatement(elseBranch);
                        return ifStatement;
                    }

                    // "while" "(" Expression ")" Statement
                    case "whileStmt": {
                        WhileStatement whileStatement = ast.newWhileStatement();
                        whileStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        whileStatement.setBody((Statement) node.getChildWithName("Statement").accept(this));
                        return whileStatement;
                    }

                    // forStmt: "for" "(" ForControl ")" Statement
                    // TODO: update it after putting labels for alternatives of forStmt
                    case "forStmt": {
                        EnhancedForStatement forStatement = ast.newEnhancedForStatement();
                        return forStatement;
//                        if (ctx.forControl().enhancedForControl() != null) {
//                            EnhancedForStatement forStatement = ast.newEnhancedForStatement();
//
//                            JavaParser.EnhancedForControlContext enhancedForControlContext = ctx.forControl().enhancedForControl();
//                            forStatement.setExpression((Expression) enhancedForControlContext.expression().accept(this));
//
//                            SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
//                            singleVariableDeclaration.setType((Type) enhancedForControlContext.typeType().accept(this));
//                            singleVariableDeclaration.setName(getIdentifier(enhancedForControlContext.variableDeclaratorId().IDENTIFIER()));
//                            singleVariableDeclaration.modifiers().addAll(createList(enhancedForControlContext.variableModifier()));
//                            List<Dimension> dimensions = getDimensions(ctx.forControl().enhancedForControl().variableDeclaratorId().dimensions());
//                            singleVariableDeclaration.extraDimensions().addAll(dimensions);
//
//                            forStatement.setParameter(singleVariableDeclaration);
//
//                            forStatement.setBody((Statement) ctx.statement().accept(this));
//                            return forStatement;
//                        } else {
//                            ForStatement forStatement = ast.newForStatement();
//
//                            if (ctx.forControl().forInit() != null) {
//                                if (ctx.forControl().forInit().localVariableDeclaration() != null) {
//                                    JavaParser.LocalVariableDeclarationContext localVariableDeclarationContext = ctx.forControl().forInit().localVariableDeclaration();
//                                    List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(localVariableDeclarationContext.variableDeclarators());
//                                    VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragments.get(0));
//                                    variableDeclarationExpression.fragments().addAll(fragments.subList(1, fragments.size()));
//                                    variableDeclarationExpression.modifiers().addAll(createList(localVariableDeclarationContext.variableModifier()));
//                                    variableDeclarationExpression.setType((Type) localVariableDeclarationContext.typeType().accept(this));
//
//                                    forStatement.initializers().add(variableDeclarationExpression);
//                                } else {
//                                    forStatement.initializers().addAll(createList(ctx.forControl().forInit().expressionList().expression()));
//                                }
//                            }
//
//                            if (ctx.forControl().expression() != null) {
//                                forStatement.setExpression((Expression) ctx.forControl().expression().accept(this));
//                            }
//
//                            if (ctx.forControl().forUpdate != null) {
//                                forStatement.updaters().addAll(createList(ctx.forControl().forUpdate.expression()));
//                            }
//
//                            Statement statement = (Statement) ctx.statement().accept(this);
//                            if (statement != null) forStatement.setBody(statement);
//
//                            return forStatement;
                    }
                }
                return null;
            }

            // VariableModifier* ReferenceType VariableDeclaratorId "=" Expression
            case "Resource": {
                VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
                variableDeclarationFragment.setName(ast.newSimpleName(node.getChildWithName("VariableDeclaratorId").childAt(0).getText(input)));
                variableDeclarationFragment.setInitializer((Expression) node.getChildWithName("Expression").accept(this));

                VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(variableDeclarationFragment);
                variableDeclarationExpression.modifiers().addAll(createList(node.getChildrenWithName("VariableModifier*")));
                variableDeclarationExpression.setType((Type) node.getChildWithName("ReferenceType").accept(this));
                return variableDeclarationExpression;
            }

            /*
             * SwitchLabel
             *   : "case" Expression ":"
             *   | "default" ":"
             */
            // TODO: after flattening constant expression, update this one
            case "SwitchLabel": {
                SwitchCase switchCase = ast.newSwitchCase();
                switchCase.setExpression(null); // default case
                if (node.hasChild("Expression")) {
                    switchCase.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                }
                return switchCase;
            }

            // CatchClause: "catch" "(" VariableModifier* CatchType Identifier ")" Block
            case "CatchClause": {
                CatchClause catchClause = ast.newCatchClause();

                SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
                singleVariableDeclaration.modifiers().addAll(createList(node.getChildWithName("VariableModifier*").children()));
                singleVariableDeclaration.setType((Type) node.getChildWithName("CatchType").accept(this));
                singleVariableDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                catchClause.setException(singleVariableDeclaration);

                catchClause.setBody((Block) node.getChildWithName("Block").accept(this));

                return catchClause;
            }

            // CatchType: {QualifiedIdentifier "|"}+
            case "CatchType": {
                List<Type> types = createList(node.getChildrenWithName("QualifiedIdentifier")).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList());
                if (types.size() == 1) {
                    return types.get(0);
                }
                UnionType unionType = ast.newUnionType();
                unionType.types().addAll(types);
                return unionType;
            }

            // Finally: "finally" Block
            case "Finally": {
                return node.getChildWithName("Block").accept(this);
            }

            case "Expression": {
                if (node.getGrammarDefinition().getLabel() == null) {
                    return ast.newNumberLiteral("1");
                }
                switch (node.getGrammarDefinition().getLabel()) {
                    case "primary": {
                        return node.childAt(0).accept(this);
                    }

                    // TODO: Complete after making the first expression an identifier
                    // Expression !brackets "(" ArgumentList? ")"
                    case "methodCall": {
//                        MethodInvocation methodInvocation = ast.newMethodInvocation();
//                        methodInvocation.setName(getIdentifier());
//                        if (ctx.expressionList() != null) {
//                            methodInvocation.arguments().addAll(createList(ctx.expressionList().expression()));
//                        }
//                        return methodInvocation;
                        return ast.newNumberLiteral("1");
                    }

                    // TODO: update this after giving them label
                    // Expression "[" Expression "]"
                    case "arrayAccess": {
                        ArrayAccess arrayAccess = ast.newArrayAccess();
                        arrayAccess.setArray((Expression) node.childAt(0).accept(this));
                        arrayAccess.setIndex((Expression) node.childAt(2).accept(this));
                        return arrayAccess;
                    }

                    // Expression ("++" | "--")
                    case "postfix": {
                        PostfixExpression postfixExpression = ast.newPostfixExpression();
                        postfixExpression.setOperand((Expression) node.getChildWithName("Expression").accept(this));
                        postfixExpression.setOperator(PostfixExpression.Operator.toOperator(node.childAt(1).getText(input)));
                        return postfixExpression;
                    }

                    // TODO: fix it
                    case "unaryPlusMinus":
                    case "prefix": {
                        PrefixExpression prefixExpression = ast.newPrefixExpression();
                        prefixExpression.setOperand((Expression) node.getChildWithName("Expression").accept(this));
                        prefixExpression.setOperator(PrefixExpression.Operator.toOperator(node.childAt(0).getText(input)));
                        return prefixExpression;
                    }

                    // "(" Type ")" Expression
                    // TODO: Rename to case expression
                    case "caseExpr": {
                        CastExpression castExpression = ast.newCastExpression();
                        castExpression.setType((Type) node.getChildWithName("Type").accept(this));
                        castExpression.setExpression((Expression) node.getChildWithName("Expresssion").accept(this));
                        return castExpression;
                    }

                    // Expression "instanceof" Type
                    case "instanceOfExpr": {
                        InstanceofExpression instanceofExpression = ast.newInstanceofExpression();
                        instanceofExpression.setLeftOperand((Expression) node.getChildWithName("Expression").accept(this));
                        instanceofExpression.setRightOperand((Type) node.getChildWithName("Type").accept(this));
                        return instanceofExpression;
                    }

                    // Expression "?" Expression ":" Expression
                    case "conditionalExpr": {
                        ConditionalExpression conditionalExpression = ast.newConditionalExpression();
                        conditionalExpression.setExpression((Expression) node.childAt(0).accept(this));
                        conditionalExpression.setThenExpression((Expression) node.childAt(2).accept(this));
                        conditionalExpression.setElseExpression((Expression) node.childAt(4).accept(this));
                        return conditionalExpression;
                    }

                    // TODO: rename ao to assignmentExpr
                    // Expression AssignmentOperator Expression
                    case "ao": {
                        Assignment assignment = ast.newAssignment();
                        assignment.setLeftHandSide((Expression) node.childAt(0).accept(this));
                        assignment.setRightHandSide((Expression) node.childAt(2).accept(this));
                        assignment.setOperator(Assignment.Operator.toOperator(node.childAt(1).getText(input)));
                        return assignment;
                    }

                    default:
                        return ast.newNumberLiteral("1");
                }
            }

            case "Primary": {
                switch (node.getGrammarDefinition().getLabel()) {
                    case "literalPrimary": {
                        return node.getChildWithName("Literal").accept(this);
                    }

                    case "parExprPrimary": {
                        ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                        parenthesizedExpression.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        return parenthesizedExpression;
                    }

                    default:
                        return ast.newNumberLiteral("1");
                }
            }

            case "IntegerLiteral":
            case "FloatingPointLiteral": {
                return ast.newNumberLiteral(node.getText(input));
            }

            case "BooleanLiteral": {
                return ast.newBooleanLiteral(Boolean.parseBoolean(node.getText(input)));
            }

            case "CharacterLiteral": {
                CharacterLiteral characterLiteral = ast.newCharacterLiteral();
                characterLiteral.setEscapedValue(node.getText(input));
                return characterLiteral;
            }

            case "StringLiteral": {
                StringLiteral stringLiteral = ast.newStringLiteral();
                stringLiteral.setEscapedValue(node.getText(input));
                return stringLiteral;
            }

            case "NullLiteral": {
                return ast.newNullLiteral();
            }

            // ConstructorDeclaration: ConstructorModifier* TypeParameters? Identifier "(" FormalParameterList? ")" Throws? Block
            case "ConstructorDeclaration": {
                MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
                constructorDeclaration.setConstructor(true);
                constructorDeclaration.modifiers().addAll(createList(node.getChildWithName("ConstructorModifier*").children()));
                if (isOptionNotEmpty(node.getChildWithName("TypeParameters?"))) {
                    constructorDeclaration.typeParameters().addAll(getTypeParameters(node.getChildWithName("TypeParameters?").childAt(0)));
                }
                constructorDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                if (isOptionNotEmpty(node.getChildWithName("FormalParameterList?"))) {
                    constructorDeclaration.parameters().addAll(getFormalParameters(node.getChildWithName("FormalParameterList?").childAt(0)));
                }
                if (isOptionNotEmpty(node.getChildWithName("Throws?"))) {
                    constructorDeclaration.thrownExceptionTypes().addAll(getThrownExceptionTypes(node.getChildWithName("Throws?").childAt(0)));
                }
                return constructorDeclaration;
            }

            // Initializer: "static"? Block
            case "Initializer": {
                Initializer initializer = ast.newInitializer();
                if (!isOptionNotEmpty(node.childAt(0))) {
                    initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
                }
                initializer.setBody((Block) node.getChildWithName("Block").accept(this));
                return initializer;
            }

            case "QualifiedIdentifier": {
                List<SimpleName> identifiers = createList(node.childAt(0).getChildrenWithName("Identifier"), SimpleName.class);

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

            /*
             * Type
             *   : PrimitiveType ("[" "]")*
             *   | ReferenceType ("[" "]")*
             */
            case "Type": {
                Type type = (Type) node.childAt(0).accept(this);
                int dimensions = getDimensionsSize(node.childAt(1));
                if (dimensions > 0) {
                    return ast.newArrayType(type, dimensions);
                }
                return type;
            }

            // Identifier TypeArguments? ( '.' Identifier TypeArguments? )*
            case "ReferenceType": {
                Type type = ast.newSimpleType(getIdentifier(node.getChildWithName("Identifier")));

                if (isOptionNotEmpty(node.childAt(1))) {
                    ParameterizedType parameterizedType = ast.newParameterizedType(type);
                    ParseTreeNode typeArgumentsNode = node.childAt(1).childAt(0);
                    if (isOptionNotEmpty(typeArgumentsNode)) {
                        parameterizedType.typeArguments().addAll(getTypeArguments(typeArgumentsNode));
                        type = parameterizedType;
                    }
                }

                for (ParseTreeNode typeArgumentsNode : node.childAt(2).getChildrenWithName("TypeArguments?")) {
                    ParameterizedType parameterizedType = ast.newParameterizedType(type);
                    if (isOptionNotEmpty(typeArgumentsNode)) {
                        parameterizedType.typeArguments().addAll(getTypeArguments(typeArgumentsNode));
                        type = parameterizedType;
                    }
                }

                return type;
            }

            /*
             * TypeArgument
             *    : simpleTypeArgument:   Type
             *    | wildCardTypeArgument: "?" (("extends" | "super") Type)?
             */
            case "TypeArgument": {
                switch (node.getGrammarDefinition().getLabel()) {
                    case "simpleTypeArgument": {
                        return node.getChildWithName("Type").accept(this);
                    }

                    case "wildCardTypeArgument": {
                        WildcardType wildcardType = ast.newWildcardType();
                        if (isOptionNotEmpty(node.childAt(1))) { // "?" (("extends" | "super") Type)?
                            String superOrExtends = node.childAt(1).childAt(0).getText(input);
                            if (superOrExtends.equals("super")) {
                                wildcardType.setUpperBound(false);
                            }
                            Type typeBound = (Type) node.childAt(1).childAt(1).accept(this);
                            wildcardType.setBound(typeBound);
                        }
                        return wildcardType;
                    }
                }
            }

            // TypeParameter: Identifier TypeBound?
            case "TypeParameter": {
                TypeParameter typeParameter = ast.newTypeParameter();

                typeParameter.setName(getIdentifier(node.getChildWithName("Identifier")));

                // TypeBound: "extends" {ReferenceType "&"}+
                if (isOptionNotEmpty(node.getChildWithName("TypeBound?"))) {
                    typeParameter.typeBounds().addAll(createList(node.getChildWithName("TypeBound?").childAt(0).childAt(1).getChildrenWithName("ReferenceType")));
                }

                return typeParameter;
            }

            case "VariableModifier":
            case "FieldModifier":
            case "MethodModifier":
            case "InterfaceModifier":
            case "ConstantModifier":
            case "AbstractMethodModifier":
            case "ConstructorModifier":
            case "ClassModifier": {
                if (node.hasChild("Annotation")) {
                    return null;
                } else {
                    return ast.newModifier(Modifier.ModifierKeyword.toKeyword(node.getText(input)));
                }
            }

            case "PrimitiveType": {
                return ast.newPrimitiveType(PrimitiveType.toCode(node.getText(input)));
            }

            case "Identifier": {
                return ast.newSimpleName(node.childAt(0).getText(input));
            }
        }

        return visitChildren(node);
    }

    @Override
    public ASTNode visit(AmbiguityNode ambiguityNode) {
        throw new RuntimeException("Ambiguity");
    }

    @Override
    public ASTNode visit(TerminalNode terminalNode) {
        return null;
    }

    @Override
    public ASTNode visit(MetaSymbolNode node) {
        return visitChildren(node);
    }

    private ASTNode visitChildren(ParseTreeNode node) {
        for (ParseTreeNode child : node.children()) {
            ASTNode result = child.accept(this);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private <T extends ASTNode> List<T> createList(List<ParseTreeNode> nodes, Class<T> clazz) {
        return (List<T>) nodes.stream().map(n -> n.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private List<ASTNode> createList(List<ParseTreeNode> nodes) {
        return nodes.stream().map(n -> n.accept(this)).filter(Objects::nonNull).collect(toList());
    }

    private SimpleName getIdentifier(ParseTreeNode node) {
        return ast.newSimpleName(node.getText(input));
    }

    public Name getQualifiedName(ParseTreeNode node) {
        List<SimpleName> identifiers = createList(node.getChildrenWithName("Identifier"), SimpleName.class);

        SimpleName simpleName = identifiers.get(0);

        if (identifiers.size() == 1) {
            return simpleName;
        }

        Name qualifier = simpleName;
        simpleName = identifiers.get(1);
        qualifier = ast.newQualifiedName(qualifier, simpleName);

        for (int i = 2; i < identifiers.size(); i++) {
            simpleName = identifiers.get(i);
            qualifier = ast.newQualifiedName(qualifier, simpleName);
        }

        return qualifier;
    }

    // {VariableDeclarator ","}+
    private List<VariableDeclarationFragment> getVariableDeclarationFragments(ParseTreeNode node) {
        List<ParseTreeNode> variableDeclaratorNodes = node.getChildrenWithName("VariableDeclarator");
        List<VariableDeclarationFragment> fragments = new ArrayList<>();
        // VariableDeclarator: VariableDeclaratorId ("=" VariableInitializer)?
        for (ParseTreeNode variableDeclaratorNode : variableDeclaratorNodes) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(getIdentifier(variableDeclaratorNode.getChildWithName("VariableDeclaratorId").getChildWithName("Identifier")));
            if (isOptionNotEmpty(variableDeclaratorNode.childAt(1))) {
                // TODO: flatten sequence inside option
                Expression expression = (Expression) variableDeclaratorNode.childAt(1).childAt(0).getChildWithName("VariableInitializer").accept(this);
                fragment.setInitializer(expression);
            }
            fragments.add(fragment);
        }
        return fragments;
    }

    // FormalParameterList: (FormalParameter ",")* LastFormalParameter
    private List<SingleVariableDeclaration> getFormalParameters(ParseTreeNode node) {
        final List<SingleVariableDeclaration> formalParameters = new ArrayList<>();

        if (isOptionNotEmpty(node.childAt(0))) {
            for (SingleVariableDeclaration variableDeclaration : createList(node.childAt(0).childAt(0).getChildrenWithName("FormalParameter"), SingleVariableDeclaration.class)) {
                formalParameters.add(variableDeclaration);
            }
        }

        formalParameters.add((SingleVariableDeclaration) node.getChildWithName("LastFormalParameter").accept(this));

        return formalParameters;
    }


    // TypeArguments: '<' {TypeArgument ","}+ '>'
    private List<Type> getTypeArguments(ParseTreeNode node) {
        return createList(node.childAt(1).children(), Type.class);
    }

    // TypeParameters: "\<" {TypeParameter ","}+ "\>"
    private List<Type> getTypeParameters(ParseTreeNode node) {
        return createList(node.childAt(1).children(), Type.class);
    }

    // Throws: "throws" {QualifiedIdentifier ","}+
    private List<Type> getThrownExceptionTypes(ParseTreeNode node) {
        return createList(node.childAt(1).children()).stream().map(name -> ast.newSimpleType((Name) name)).collect(toList());
    }

    private List<Type> getTypeList(ParseTreeNode node) {
        return createList(node.childAt(0).getChildrenWithName("Type"), Type.class);
    }

    private List<ParseTreeNode> getOption(ParseTreeNode node) {
        return node.childAt(0).children();
    }

    // SwitchBlockStatementGroup = = SwitchLabel+ BlockStatement+
    private List<Statement> getSwitchBlockStatements(ParseTreeNode node) {
        List<Statement> switchLabels = createList(node.getChildWithName("SwitchLabel+").children(), Statement.class);
        List<Statement> blockStatements = createList(node.getChildWithName("BlockStatement+").children(), Statement.class);
        List<Statement> result = new ArrayList<>(switchLabels);
        result.addAll(blockStatements);
        return result;
    }

    // ('[' ']')*
    private int getDimensionsSize(ParseTreeNode node) {
        if (node.children().size() == 0) {
            return 0;
        }
        return node.childAt(0).children().size() / 2;
    }

    private List<Dimension> getDimensions(ParseTreeNode node) {
        if (node == null) return emptyList();
        return IntStream.range(0, getDimensionsSize(node)).mapToObj(i -> ast.newDimension()).collect(toList());
    }

    private boolean isOptionNotEmpty(ParseTreeNode node) {
        return node.children().size() > 0;
    }
}
