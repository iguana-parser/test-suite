package iguana;

import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.core.dom.Block;
import org.iguana.grammar.symbol.*;
import org.iguana.parsetree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class IguanaToJavaParseTreeVisitor implements ParseTreeVisitor<Object> {

    private AST ast = AST.newAST(AST.JLS10);
    private Input input;

    public IguanaToJavaParseTreeVisitor(Input input) {
        this.input = input;
    }

    @Override
    public Object visit(NonterminalNode node) {
        switch (node.getName()) {

            // CompilationUnit: PackageDeclaration? ImportDeclaration* TypeDeclaration*
            case "CompilationUnit": {
                CompilationUnit compilationUnit = ast.newCompilationUnit();
                PackageDeclaration packageDeclaration = (PackageDeclaration) node.getChildWithName("PackageDeclaration?").accept(this);
                if (packageDeclaration != null) {
                    compilationUnit.setPackage(packageDeclaration);
                }
                compilationUnit.imports().addAll((List<ImportDeclaration>) node.getChildWithName("ImportDeclaration*").accept(this));
                compilationUnit.types().addAll((List<TypeDeclaration>) node.getChildWithName("TypeDeclaration*").accept(this));
                return compilationUnit;
            }

            // Annotation* "package"  QualifiedIdentifier ";"
            case "PackageDeclaration": {
                PackageDeclaration packageDeclaration = ast.newPackageDeclaration();
                packageDeclaration.setName((Name) node.getChildWithName("QualifiedIdentifier").accept(this));
                return packageDeclaration;
            }

            // ImportDeclaration: "import"  "static"?  {Identifier "."}+ ("." "*")? ";"
            case "ImportDeclaration": {
                ImportDeclaration importDeclaration = ast.newImportDeclaration();
                if (node.childAt(1).children().size() > 0) { // "static"?
                    importDeclaration.setStatic(true);
                }
                importDeclaration.setName(getQualifiedName(node.childAt(2)));
                if (node.childAt(3).children().size() > 0) { // ("." "*")?
                    importDeclaration.setOnDemand(true);
                }
                return importDeclaration;
            }

            // NormalClassDeclaration: ClassModifier* "class" Identifier TypeParameters? ("extends" Type)? ("implements" TypeList)? ClassBody;
            case "NormalClassDeclaration": {
                TypeDeclaration classDeclaration = ast.newTypeDeclaration();
                classDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("ClassModifier*")));
                classDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<Type> typeParameters = (List<Type>) node.getChildWithName("TypeParameters?").accept(this);
                if (typeParameters != null) {
                    classDeclaration.typeParameters().addAll(typeParameters);
                }
                classDeclaration.bodyDeclarations().addAll((List<BodyDeclaration>) node.getChildWithName("ClassBody").accept(this));

                List<Type> type = (List<Type>) node.childAt(4).accept(this);
                if (type != null) { // ("extends" Type)?
                    classDeclaration.setSuperclassType(type.get(0));
                }
                return classDeclaration;
            }

            // EnumDeclaration: ClassModifier* "enum" Identifier ("implements" TypeList)? EnumBody
            case "EnumDeclaration": {
                EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
                enumDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("ClassModifier*")));
                enumDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<Type> typeList = (List<Type>) node.childAt(3).accept(this);
                if (typeList != null) {
                    enumDeclaration.superInterfaceTypes().addAll(typeList);
                }

                List<BodyDeclaration> enumConstants = (List<BodyDeclaration>) node.getChildWithName("EnumBody").childAt(1).accept(this);
                enumDeclaration.enumConstants().addAll(enumConstants);

                List<BodyDeclaration> enumBodyDeclararions = (List<BodyDeclaration>) node.getChildWithName("EnumBody").childAt(3).accept(this);
                if (enumBodyDeclararions != null) {
                    enumDeclaration.bodyDeclarations().addAll(enumBodyDeclararions);
                }

                return enumDeclaration;
            }

            // EnumConstant: Annotation* Identifier Arguments? ClassBody?
            case "EnumConstant": {
                EnumConstantDeclaration enumConstantDeclaration = ast.newEnumConstantDeclaration();

                enumConstantDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("Annotation*")));
                enumConstantDeclaration.setName((SimpleName) node.childAt(1).accept(this));

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
                interfaceDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));
                interfaceDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("InterfaceModifier*")));
                return interfaceDeclaration;
            }

            // InterfaceModifier* "@" "interface" Identifier AnnotationTypeBody
            case "AnnotationTypeDeclaration": {
                AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
                annotationTypeDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));
                annotationTypeDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("InterfaceModifier*")));
                // TODO: add annotation type bodies
                return annotationTypeDeclaration;
            }

            // FieldModifier* Type {VariableDeclarator ","}+ ";"
            case "FieldDeclaration": {
                List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
                FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
                fieldDeclaration.setType((Type) node.getChildWithName("Type").accept(this));
                fieldDeclaration.fragments().addAll(fragments.subList(1, fragments.size()));
                return fieldDeclaration;
            }

            // VariableDeclaratorId ("=" VariableInitializer)?
            case "VariableDeclarator": {
                VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
                ParseTreeNode variableDeclaratorIdNode = node.getChildWithName("VariableDeclaratorId");
                fragment.setName((SimpleName) variableDeclaratorIdNode.getChildWithName("Identifier").accept(this));
                fragment.extraDimensions().addAll(getDimensions(variableDeclaratorIdNode.childAt(1)));

                List<Expression> expressions = (List<Expression>) node.childAt(1).accept(this);
                if (expressions != null) {
                    fragment.setInitializer(expressions.get(0));
                }
                return fragment;
            }

            // MethodDeclaration: MethodModifier* TypeParameters? Result MethodDeclarator Throws? MethodBody
            // MethodDeclarator:  Identifier "(" FormalParameterList? ")" ("[" "]")*
            case "MethodDeclaration": {
                MethodDeclaration methodDeclaration = ast.newMethodDeclaration();

                methodDeclaration.modifiers().addAll((List<IExtendedModifier>) node.getChildWithName("MethodModifier*").accept(this));

                List<Type> typeParameters = (List<Type>) node.getChildWithName("TypeParameters?").accept(this);
                if (typeParameters != null) {
                    methodDeclaration.typeParameters().addAll(typeParameters);
                }

                methodDeclaration.setReturnType2((Type) node.getChildWithName("Result").accept(this));

                ParseTreeNode methodDeclarator = node.getChildWithName("MethodDeclarator");
                methodDeclaration.setName((SimpleName) methodDeclarator.getChildWithName("Identifier").accept(this));

                List<SingleVariableDeclaration> parameters = (List<SingleVariableDeclaration>) methodDeclarator.getChildWithName("FormalParameterList?").accept(this);
                if (parameters != null) {
                    methodDeclaration.parameters().addAll(parameters);
                }

                methodDeclaration.extraDimensions().addAll(getDimensions(methodDeclarator.childAt(4)));

                List<Type> exceptionTypes = (List<Type>) node.getChildWithName("Throws?").accept(this);
                if (exceptionTypes != null) {
                    methodDeclaration.thrownExceptionTypes().addAll(exceptionTypes);
                }

                methodDeclaration.setBody((Block) node.getChildWithName("MethodBody").accept(this));

                return methodDeclaration;
            }

            // "throws" {QualifiedIdentifier ","}+
            case "Throws": {
                return ((List<SimpleName>) node.childAt(1).accept(this)).stream().map(name -> ast.newSimpleType(name)).collect(toList());
            }

            // (FormalParameter ",")* LastFormalParameter
            case "FormalParameterList": {
                List<SingleVariableDeclaration> formalParameters = (List<SingleVariableDeclaration>) node.childAt(0).accept(this);
                SingleVariableDeclaration lastFormalParameter = (SingleVariableDeclaration) node.childAt(1).accept(this);
                formalParameters.add(lastFormalParameter);
                return formalParameters;
            }

            // FormalParameter: VariableModifier* Type VariableDeclaratorId
            case "FormalParameter": {
                SingleVariableDeclaration singleVariableDeclaration = ast.newSingleVariableDeclaration();
                singleVariableDeclaration.setType((Type) node.getChildWithName("Type").accept(this));
                singleVariableDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
                ParseTreeNode variableDeclaratorId = node.getChildWithName("VariableDeclaratorId");
                singleVariableDeclaration.extraDimensions().addAll(getDimensions(variableDeclaratorId.childAt(1)));
                singleVariableDeclaration.setName((SimpleName) variableDeclaratorId.getChildWithName("Identifier").accept(this));
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
                singleVariableDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
                singleVariableDeclaration.setVarargs(true);
                singleVariableDeclaration.setName((SimpleName) node.getChildWithName("VariableDeclaratorId").getChildWithName("Identifier").accept(this));
                return singleVariableDeclaration;
            }

            case "Result": {
                if (node.childAt(0).getName().equals("Type")) {
                    return (Type) node.childAt(0).accept(this);
                }
                return ast.newPrimitiveType(PrimitiveType.VOID);
            }

            case "Block": {
                Block block = ast.newBlock();
                List<ASTNode> blockStatements = (List<ASTNode>) node.getChildWithName("BlockStatement*").accept(this);
                for (ASTNode blockStatement : blockStatements) {
                    if (blockStatement instanceof TypeDeclaration) {
                        block.statements().add(ast.newTypeDeclarationStatement((TypeDeclaration) blockStatement));
                    } else {
                        block.statements().add(blockStatement);
                    }
                }
                return block;
            }

            case "BlockStatement": {
                if (node.getGrammarDefinition().getLabel() != null) { // localVariableDeclaration
                    return getLocalVariableDeclarationStatement(node.childAt(0));
                }
                return node.childAt(0).accept(this);
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

                        Expression message = (Expression) node.childAt(1).accept(this);
                        if (message != null) {
                            assertStatement.setMessage(message);
                        }
                        return assertStatement;
                    }

                    // "switch" "(" Expression ")" "{" SwitchBlockStatementGroup* SwitchLabel* "}"
                    case "switchStmt": {
                        SwitchStatement switchStatement = ast.newSwitchStatement();
                        switchStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));

                        List<Statement> statements = node.getChildWithName("SwitchBlockStatementGroup*").children().stream().flatMap(stmt -> getSwitchBlockStatements(stmt).stream()).collect(toList());
                        switchStatement.statements().addAll(statements);
                        switchStatement.statements().addAll((List<Statement>) node.getChildWithName("SwitchLabel*").accept(this));
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

                        SimpleName identifier = (SimpleName) node.getChildWithName("Identifier?").accept(this);
                        if (identifier != null) {
                            breakStatement.setLabel(identifier);
                        }
                        return breakStatement;
                    }

                    // "continue" Identifier? ";"
                    case "continueStmt": {
                        ContinueStatement continueStatement = ast.newContinueStatement();
                        SimpleName identifier = (SimpleName) node.getChildWithName("Identifier?").accept(this);
                        if (identifier != null) {
                            continueStatement.setLabel(identifier);
                        }
                        return continueStatement;
                    }

                    // "return" Expression? ";"
                    case "returnStmt": {
                        ReturnStatement returnStatement = ast.newReturnStatement();
                        Expression expression = (Expression) node.getChildWithName("Expression?").accept(this);
                        if (expression != null) {
                            returnStatement.setExpression(expression);
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
                        tryStatement.resources().addAll((List<Expression>) node.getChildWithName("ResourceSpecification").accept(this));
                        tryStatement.setBody((Block) node.getChildWithName("Block").accept(this));
                        tryStatement.catchClauses().addAll((List<CatchClause>) node.getChildWithName("CatchClause*").accept(this));

                        Block block = (Block) node.getChildWithName("Finally?").accept(this);
                        if (block != null) {
                            tryStatement.setFinally(block);
                        }
                        return tryStatement;
                    }

                    // "(" { Resource ";"}+ ";"? ")"
                    case "ResourceSpecification": {
                        return node.childAt(1).accept(this);
                    }

                    // Identifier ":" Statement
                    case "labelStmt": {
                        LabeledStatement labeledStatement = ast.newLabeledStatement();
                        labeledStatement.setLabel((SimpleName) node.getChildWithName("Identifier").accept(this));
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
                    case "ifElseStmt": {
                        IfStatement ifStatement = ast.newIfStatement();
                        ifStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        Statement thenBranch = (Statement) node.childAt(4).accept(this);
                        if (thenBranch != null)
                            ifStatement.setThenStatement(thenBranch);
                        Statement elseBranch = (Statement) node.childAt(6).accept(this);
                        if (elseBranch != null)
                            ifStatement.setElseStatement(elseBranch);
                        return ifStatement;
                    }

                    // "while" "(" Expression ")" Statement
                    case "whileStmt": {
                        WhileStatement whileStatement = ast.newWhileStatement();
                        whileStatement.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        whileStatement.setBody((Statement) node.getChildWithName("Statement").accept(this));
                        return whileStatement;
                    }

                    case "forStmt": {
                        ParseTreeNode forControlNode = node.getChildWithName("ForControl");
                        switch (((Rule) forControlNode.getGrammarDefinition()).getLabel()) {

                            // ForInit? ";" Expression? ";" ForUpdate?
                            case "traditionalFor": {
                                ForStatement forStatement = ast.newForStatement();
//
//                                ParseTreeNode forInit = forControlNode.getChildWithName("ForInit?");
//                                if (isOptionNotEmpty(forInit)) {
//                                    if (forInit.hasChild("LocalVariableDeclaration")) {
//                                        ParseTreeNode localVariableDeclarationNode = forInit.getChildWithName("LocalVariableDeclaration");
//                                        List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(localVariableDeclarationContext.variableDeclarators());
//                                        VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragments.get(0));
//                                        variableDeclarationExpression.fragments().addAll(fragments.subList(1, fragments.size()));
//                                        variableDeclarationExpression.modifiers().addAll(createList(localVariableDeclarationContext.variableModifier()));
//                                        variableDeclarationExpression.setType((Type) localVariableDeclarationContext.typeType().accept(this));
//
//                                        forStatement.initializers().add(variableDeclarationExpression);
//                                    } else {
//                                        forStatement.initializers().addAll(createList(ctx.forControl().forInit().expressionList().expression()));
//                                    }
//                                }
//
//                                if (isOptionNotEmpty(forControlNode.getChildWithName("Expression?"))) {
//                                    forStatement.setExpression((Expression) ctx.forControl().expression().accept(this));
//                                }
//
//                                if (isOptionNotEmpty(forControlNode.getChildWithName("ForUpdate?"))) {
//                                    forStatement.updaters().addAll(createList(ctx.forControl().forUpdate.expression()));
//                                }
//
//                                Statement statement = (Statement) ctx.statement().accept(this);
//                                if (statement != null) forStatement.setBody(statement);
//
                                return forStatement;
                            }

                            // FormalParameter ":" Expression
                            case "enhancedFor": {
                                EnhancedForStatement forStatement = ast.newEnhancedForStatement();

                                forStatement.setExpression((Expression) forControlNode.getChildWithName("Expression").accept(this));
                                SingleVariableDeclaration singleVariableDeclaration = (SingleVariableDeclaration) forControlNode.getChildWithName("FormalParameter").accept(this);
                                forStatement.setParameter(singleVariableDeclaration);

                                forStatement.setBody((Statement) node.getChildWithName("Statement").accept(this));
                                return forStatement;
                            }
                        }
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
                variableDeclarationExpression.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
                variableDeclarationExpression.setType((Type) node.getChildWithName("ReferenceType").accept(this));
                return variableDeclarationExpression;
            }

            /*
             * SwitchLabel
             *   : "case" Expression ":"
             *   | "default" ":"
             */
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
                singleVariableDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
                singleVariableDeclaration.setType((Type) node.getChildWithName("CatchType").accept(this));
                singleVariableDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));
                catchClause.setException(singleVariableDeclaration);

                catchClause.setBody((Block) node.getChildWithName("Block").accept(this));

                return catchClause;
            }

            // CatchType: {QualifiedIdentifier "|"}+
            case "CatchType": {
                List<Type> types = ((List<SimpleName>) node.childAt(0).accept(this)).stream().map(name -> ast.newSimpleType(name)).collect(toList());
                if (types.size() == 1) {
                    return types.get(0);
                }
                UnionType unionType = ast.newUnionType();
                unionType.types().addAll(types);
                return unionType;
            }

            // Finally: "finally" Block
            case "Finally": {
                return (Block) node.getChildWithName("Block").accept(this);
            }

            case "Expression": {
                // Infix expression
                if (node.getGrammarDefinition().getLabel() == null) {
                    InfixExpression infixExpression = ast.newInfixExpression();
                    infixExpression.setLeftOperand((Expression) node.childAt(0).accept(this));
                    infixExpression.setRightOperand((Expression) node.childAt(2).accept(this));
                    infixExpression.setOperator(InfixExpression.Operator.toOperator(node.childAt(1).getText(input)));
                    return infixExpression;
                }

                switch (node.getGrammarDefinition().getLabel()) {

                    // Expression "." Selector
                    case "fieldAccess": {
                        Expression expression = (Expression) node.childAt(0).accept(this);
                        return getFieldAccess(expression, (NonterminalNode) node.childAt(2));
                    }

                    // MethodInvocation
                    case "methodCall": {
                        return node.childAt(0).accept(this);
                    }

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

                    case "prefix": {
                        PrefixExpression prefixExpression = ast.newPrefixExpression();
                        prefixExpression.setOperand((Expression) node.getChildWithName("Expression").accept(this));
                        prefixExpression.setOperator(PrefixExpression.Operator.toOperator(node.childAt(0).getText(input)));
                        return prefixExpression;
                    }

                    // "new" (ClassInstanceCreationExpression | ArrayCreationExpression)
                    case "newClass": {
                        return node.childAt(1).accept(this);
                    }

                    // "(" Type ")" Expression
                    case "castExpr": {
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

                    // Expression AssignmentOperator Expression
                    case "assignmentExpr": {
                        Assignment assignment = ast.newAssignment();
                        assignment.setLeftHandSide((Expression) node.childAt(0).accept(this));
                        assignment.setRightHandSide((Expression) node.childAt(2).accept(this));
                        assignment.setOperator(Assignment.Operator.toOperator(node.childAt(1).getText(input)));
                        return assignment;
                    }

                    case "primaryExpr": {
                        return node.childAt(0).accept(this);
                    }

                    // Expression op Expression
                    default: throw new RuntimeException("Unexpected exception type");
                }
            }

            // TypeArguments? TypeDeclSpecifier TypeArgumentsOrDiamond? Arguments ClassBody?
            case "ClassInstanceCreationExpression": {
                ClassInstanceCreation classInstanceCreation = ast.newClassInstanceCreation();

                List<Type> typeArguments = (List<Type>) node.getChildWithName("TypeArguments?").accept(this);
                if (typeArguments != null) {
                    classInstanceCreation.typeArguments().addAll(typeArguments);
                }

                Type type = (Type) node.getChildWithName("TypeDeclSpecifier").accept(this);
                List<Type> typeArgumentsOrDiamond = (List<Type>) node.getChildWithName("TypeArgumentsOrDiamond?").accept(this);
                if (typeArgumentsOrDiamond != null) {
                    type = ast.newParameterizedType(type);
                    ((ParameterizedType) type).typeArguments().addAll(typeArgumentsOrDiamond);
                }

                classInstanceCreation.setType(type);
                classInstanceCreation.arguments().addAll((List<Expression>) node.getChildWithName("Arguments").accept(this));

                List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.getChildWithName("ClassBody?").accept(this);
                if (bodyDeclarations != null) {
                    AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                    anonymousClassDeclaration.bodyDeclarations().addAll(bodyDeclarations);
                    classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
                }
                return classInstanceCreation;
            }

            // TypeDeclSpecifier: Identifier (TypeArguments? "." Identifier)*
            case "TypeDeclSpecifier": {
                Type type = ast.newSimpleType((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<Object> rest = (List<Object>) node.childAt(1).accept(this);

                for (Object n : rest) {
                    if (node instanceof List) {
                        type = ast.newParameterizedType(type);
                        ((ParameterizedType) type).typeArguments().addAll((List<Type>) n);
                    }
                    else { // Identifier
                        type = ast.newQualifiedType(type, (SimpleName) n);
                    }
                }

                return type;
            }

            // TypeArgumentsOrDiamond
            //        = "<" ">"
            //        | TypeArguments
            case "TypeArgumentsOrDiamond": {
                if (node.childAt(0).getName().equals("TypeArguments")) {
                    return node.childAt(0).accept(this);
                }
                return emptyList();
            }

            // TypeArguments: '<' {TypeArgument ","}+ '>'
            case "TypeArguments": {
                return node.childAt(1).accept(this);
            }

            // ClassBody: "{" ClassBodyDeclaration* "}"
            case "ClassBody": {
                List<BodyDeclaration> bodyDeclarations = new ArrayList<>();
                bodyDeclarations.addAll((List<BodyDeclaration>) node.getChildWithName("ClassBodyDeclaration*").accept(this));
                return bodyDeclarations;
            }

            /*
             * ArrayCreationExpression
             *   = (PrimitiveType | ReferenceType) ("[" Expression "]")+ ("[" "]")*
             *   | (PrimitiveType | ReferenceTypeNonArrayType) ("[" "]")+ ArrayInitializer
             */
            case "ArrayCreationExpression": {
                ArrayCreation arrayCreation = ast.newArrayCreation();
                if (node.hasChild("ArrayInitializer")) { // Second alternative
                    int dimensions = getDimensionsSize(node.childAt(1));
                    arrayCreation.setType(ast.newArrayType((Type) node.childAt(0).accept(this), dimensions));
                    arrayCreation.setInitializer((ArrayInitializer) node.getChildWithName("ArrayInitializer").accept(this));
                } else {
                    int dimensions = getDimensionsSize(node.childAt(1));
                    arrayCreation.setType(ast.newArrayType((Type) node.childAt(0).accept(this), dimensions));
                    arrayCreation.dimensions().addAll((List<Expression>) node.childAt(1).accept(this));
                }
                return arrayCreation;
            }

            // "{"  {VariableInitializer ","}* ","? "}"
            case "ArrayInitializer": {
                ArrayInitializer arrayInitializer = ast.newArrayInitializer();
                arrayInitializer.expressions().addAll((List<Expression>) node.childAt(1).accept(this));
                return arrayInitializer;
            }

            case "Primary": {
                switch (node.getGrammarDefinition().getLabel()) {

                    case "literalPrimary": {
                        return node.getChildWithName("Literal").accept(this);
                    }

                    // (QualifiedIdentifier ".")? "this"
                    case "thisPrimary": {
                        ThisExpression thisExpression = ast.newThisExpression();
                        List<Name> name = (List<Name>) node.childAt(0).accept(this);
                        if (name != null) {
                            thisExpression.setQualifier(name.get(0));
                        }
                        return thisExpression;
                    }

                    case "superPrimary": {
                        return node.getChildWithName("SuperSuffix").accept(this);
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

                    case "parExprPrimary": {
                        ParenthesizedExpression parenthesizedExpression = ast.newParenthesizedExpression();
                        parenthesizedExpression.setExpression((Expression) node.getChildWithName("Expression").accept(this));
                        return parenthesizedExpression;
                    }

                    default:
                        throw new RuntimeException("Unexpected primary " + node);
                }
            }

            case "SuperSuffix": {
                List<Expression> arguments = (List<Expression>) node.getChildWithName("Arguments?").accept(this);
                if (arguments != null) {
                    SuperMethodInvocation superMethodInvocation = ast.newSuperMethodInvocation();
                    superMethodInvocation.setName(getIdentifier(node.getChildWithName("Identifier")));
                    return superMethodInvocation;
                } else {
                    SuperFieldAccess superFieldAccess = ast.newSuperFieldAccess();
                    superFieldAccess.setName(getIdentifier(node.getChildWithName("Identifier")));
                    return superFieldAccess;
                }
            }

            // Identifier Arguments
            case "MethodInvocation": {
                MethodInvocation methodInvocation = ast.newMethodInvocation();
                methodInvocation.setName((SimpleName) node.getChildWithName("Identifier").accept(this));
                methodInvocation.arguments().addAll((List<Expression>) node.getChildWithName("Arguments").accept(this));
                return methodInvocation;
            }

            case "Literal": {
                switch (node.getGrammarDefinition().getLabel()) {
                    case "integerLiteral":
                    case "floatLiteral": {
                        return ast.newNumberLiteral(node.getText(input));
                    }

                    case "booleanLiteral": {
                        return ast.newBooleanLiteral(Boolean.parseBoolean(node.getText(input)));
                    }

                    case "characterLiteral": {
                        CharacterLiteral characterLiteral = ast.newCharacterLiteral();
                        characterLiteral.setEscapedValue(node.getText(input));
                        return characterLiteral;
                    }

                    case "stringLiteral": {
                        StringLiteral stringLiteral = ast.newStringLiteral();
                        stringLiteral.setEscapedValue(node.getText(input));
                        return stringLiteral;
                    }

                    case "nullLiteral": {
                        return ast.newNullLiteral();
                    }
                }
            }

            // ConstructorDeclaration: ConstructorModifier* TypeParameters? Identifier "(" FormalParameterList? ")" Throws? Block
            case "ConstructorDeclaration": {
                MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
                constructorDeclaration.setConstructor(true);
                constructorDeclaration.modifiers().addAll(getModifiers(node.getChildWithName("ConstructorModifier*")));

                List<TypeParameter> typeParameters = (List<TypeParameter>) node.getChildWithName("TypeParameters?").accept(this);
                if (typeParameters != null) {
                    constructorDeclaration.typeParameters().addAll(typeParameters);
                }
                constructorDeclaration.setName((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<SingleVariableDeclaration> formalParameters = (List<SingleVariableDeclaration>) node.getChildWithName("FormalParameterList?").accept(this);
                if (formalParameters != null) {
                    constructorDeclaration.parameters().addAll(formalParameters);
                }

                List<Type> exceptionTypes = (List<Type>) node.getChildWithName("Throws?").accept(this);
                if (exceptionTypes != null) {
                    constructorDeclaration.thrownExceptionTypes().addAll(exceptionTypes);
                }
                return constructorDeclaration;
            }

            // Initializer: "static"? Block
            case "Initializer": {
                Initializer initializer = ast.newInitializer();
                if (node.childAt(0).children().size() > 0) {
                    initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
                }
                initializer.setBody((Block) node.getChildWithName("Block").accept(this));
                return initializer;
            }

            // {Identifier "."}+
            case "QualifiedIdentifier": {
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
                Type type = ast.newSimpleType((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<Type> typeArguments = (List<Type>) node.childAt(1).accept(this);
                if (typeArguments != null) {
                    ParameterizedType parameterizedType = ast.newParameterizedType(type);
                    parameterizedType.typeArguments().addAll(typeArguments);
                    type = parameterizedType;
                }

                List<ASTNode> rest = (List<ASTNode>) node.childAt(2).accept(this);

                for (Object n : rest) {
                    if (n instanceof SimpleName) { // Identifier
                        type = ast.newQualifiedType(type, (SimpleName) n);
                    } else {
                        ParameterizedType parameterizedType = ast.newParameterizedType(type);
                        parameterizedType.typeArguments().addAll((List<Type>) n);
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
                        return (Type) node.getChildWithName("Type").accept(this);
                    }

                    case "wildCardTypeArgument": {
                        WildcardType wildcardType = ast.newWildcardType();
                        if (node.childAt(1).children().size() > 0) { // "?" (("extends" | "super") Type)?
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

                typeParameter.setName((SimpleName) node.getChildWithName("Identifier").accept(this));

                List<Type> typeBounds = (List<Type>) node.getChildWithName("TypeBound?").accept(this);
                if (typeBounds != null) {
                    typeParameter.typeBounds().addAll(typeBounds);
                }

                return typeParameter;
            }

            // TypeBound: "extends" {ReferenceType "&"}+
            case "TypeBound": {
                return node.childAt(1).accept(this);
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

    private Object visitChildren(ParseTreeNode node) {
        List<Object> result = node.children().stream()
                .map(child -> child.accept(this))
                .filter(Objects::nonNull)
                .collect(toList());

        if (result.isEmpty()) {
            return null;
        }

        if (result.size() == 1) {
            return result.get(0);
        }

        return result;
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
    public Object visit(MetaSymbolNode node) {
        Object definition = node.getGrammarDefinition();
        if (definition instanceof Star || definition instanceof Plus || definition instanceof Sequence) {
            List<Object> result = new ArrayList<>();
            for (ParseTreeNode child : node.children()) {
                Object childResult = child.accept(this);
                if (childResult != null) {
                    if (childResult instanceof List) {
                        result.addAll((List<?>) childResult);
                    } else {
                        result.add(childResult);
                    }
                }
            }
            return result;
        }
        else if (definition instanceof Alt) {
            return node.childAt(0).accept(this);
        }
        else { // Opt
            if (node.children().size() == 0) {
                return null;
            }
            return node.childAt(0).accept(this);
        }
    }

    // {Identifier "."}+;
    private Name getQualifiedName(ParseTreeNode node) {
        List<SimpleName> identifiers = (List<SimpleName>) node.accept(this);

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

    private SimpleName getIdentifier(ParseTreeNode node) {
        return (SimpleName) ast.newSimpleName(node.getText(input));
    }

    private List<IExtendedModifier> getModifiers(ParseTreeNode node) {
        return (List<IExtendedModifier>) node.accept(this);
    }

    // SwitchBlockStatementGroup = = SwitchLabel+ BlockStatement+
    private List<Statement> getSwitchBlockStatements(ParseTreeNode node) {
        List<Statement> switchLabels = (List<Statement>) node.getChildWithName("SwitchLabel+").accept(this);
        List<Statement> blockStatements = (List<Statement>) node.getChildWithName("BlockStatement+").accept(this);
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
                Expression result = (Expression) node.childAt(0).accept(this);
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

                List<BodyDeclaration> bodyDeclarations = (List<BodyDeclaration>) node.getChildWithName("ClassBody?").accept(this);
                if (bodyDeclarations != null) {
                    AnonymousClassDeclaration anonymousClassDeclaration = ast.newAnonymousClassDeclaration();
                    anonymousClassDeclaration.bodyDeclarations().addAll(bodyDeclarations);
                    classInstanceCreation.setAnonymousClassDeclaration(anonymousClassDeclaration);
                }

                classInstanceCreation.setExpression(expression);
                Type type = ast.newSimpleType(getIdentifier(node.getChildWithName("Identifier")));

                List<Expression> arguments = (List<Expression>) node.getChildWithName("Arguments").accept(this);
                classInstanceCreation.arguments().addAll(arguments);

                List<Type> typeArguments = (List<Type>) node.getChildWithName("TypeArguments?").accept(this);
                if (typeArguments != null) {
                    classInstanceCreation.typeArguments().addAll(typeArguments);
                }

                List<Type> typeArgumentsOrDiamond = (List<Type>) node.getChildWithName("TypeArgumentsOrDiamond?").accept(this);
                if (typeArgumentsOrDiamond != null) {
                    type = ast.newParameterizedType(type);
                    ((ParameterizedType) type).typeArguments().addAll(typeArgumentsOrDiamond);
                }

                classInstanceCreation.setType(type);
                return classInstanceCreation;
            }

            case "genericSelector": {
            }

            default:
                throw new RuntimeException("Unknown Selector: " + node);
        }
    }

    // LocalVariableDeclaration: VariableModifier* Type {VariableDeclarator ","}+
    private VariableDeclarationStatement getLocalVariableDeclarationStatement(ParseTreeNode node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
        VariableDeclarationStatement variableDeclarationStatement = ast.newVariableDeclarationStatement(fragments.get(0));
        for (int i = 1; i < fragments.size(); i++) {
            variableDeclarationStatement.fragments().add(fragments.get(i));
        }
        variableDeclarationStatement.setType((Type) node.getChildWithName("Type").accept(this));
        variableDeclarationStatement.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
        return variableDeclarationStatement;
    }

    // LocalVariableDeclaration: VariableModifier* Type {VariableDeclarator ","}+
    private VariableDeclarationExpression getLocalVariableDeclarationExpression(ParseTreeNode node) {
        List<VariableDeclarationFragment> fragments = (List<VariableDeclarationFragment>) node.childAt(2).accept(this);
        VariableDeclarationExpression variableDeclarationExpression = ast.newVariableDeclarationExpression(fragments.get(0));
        variableDeclarationExpression.fragments().addAll(fragments.subList(1, fragments.size()));
        variableDeclarationExpression.setType((Type) node.getChildWithName("Type").accept(this));
        variableDeclarationExpression.modifiers().addAll(getModifiers(node.getChildWithName("VariableModifier*")));
        return variableDeclarationExpression;
    }

}
