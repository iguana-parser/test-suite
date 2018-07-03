package iguana;


import com.sun.xml.internal.ws.wsdl.writer.document.soap.Body;
import iguana.utils.input.Input;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.jdt.core.dom.*;
import org.iguana.parsetree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

            case "NormalClassDeclaration": {
                TypeDeclaration classDeclaration = ast.newTypeDeclaration();
                classDeclaration.modifiers().addAll(createList(node.getChildWithName("ClassModifier*").children()));
                classDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
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

            case "AnnotationTypeDeclaration": {
                AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
                annotationTypeDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                annotationTypeDeclaration.modifiers().addAll(createList(node.getChildWithName("InterfaceModifier*").children()));
                return annotationTypeDeclaration;
            }

            // FieldModifier* Type {VariableDeclarator ","}+ ";"
            case "FieldDeclaration": {
                List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(node.childAt(2));
                FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(fragments.get(0));
                return fieldDeclaration;
            }

            case "MethodDeclaration": {
                MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
                return methodDeclaration;
            }

            case "ConstructorDeclaration": {
                MethodDeclaration constructorDeclaration = ast.newMethodDeclaration();
                constructorDeclaration.setConstructor(true);
                return constructorDeclaration;
            }

            case "Initializer": {
                Initializer initializer = ast.newInitializer();
                if (!isOptionNotEmpty(node.childAt(0))) {
                    initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
                }
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

            case "Type": {
                Type type = (Type) node.childAt(0).accept(this);
                int dimensions = getDimensions(node.childAt(1));
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
                    ParseTreeNode typeArgumentsParseTree = node.childAt(1);
                    if (isOptionNotEmpty(typeArgumentsParseTree)) {
                        parameterizedType.typeArguments().addAll(getTypeArguments(typeArgumentsParseTree));
                        type = parameterizedType;
                    }
                }

                for (int i = 0; i < node.childAt(1).children().size() / 3; i++) {
                    ParameterizedType parameterizedType = ast.newParameterizedType(type);
                    ParseTreeNode typeArgumentsParseTree = node.childAt(1).childAt(i);
                    if (isOptionNotEmpty(typeArgumentsParseTree)) {
                        parameterizedType.typeArguments().addAll(getTypeArguments(typeArgumentsParseTree));
                        type = parameterizedType;
                    }
                }

                return type;
            }
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
                    }
                }
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

            /*
             * Type
             *    : BasicType ("[" "]")*
             *    | ReferenceType ("[" "]")*
             */

            case "ArrayType": {
                return null;
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

    private List<VariableDeclarationFragment> getVariableDeclarationFragments(ParseTreeNode node) {
        List<ParseTreeNode> variableDeclarators = node.getChildrenWithName("VariableDeclarator");
        List<VariableDeclarationFragment> fragments = new ArrayList<>();
        for (ParseTreeNode variableDeclarator : variableDeclarators) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(getIdentifier(variableDeclarator.getChildWithName("VariableDeclaratorId").getChildWithName("Identifier")));
            fragments.add(fragment);
        }
        return fragments;
    }

    private List<Type> getTypeArguments(ParseTreeNode node) {
        return createList(node.childAt(1).children(), Type.class);
    }

    private List<Type> getTypeList(ParseTreeNode node) {
        return createList(node.childAt(0).getChildrenWithName("Type"), Type.class);
    }

    private List<ParseTreeNode> getOption(ParseTreeNode node) {
        return node.childAt(0).children();
    }

    private int getDimensions(ParseTreeNode node) {
        return node.children().size() / 2;
    }

    private boolean isOptionNotEmpty(ParseTreeNode node) {
        return node.children().size() > 0;
    }
}
