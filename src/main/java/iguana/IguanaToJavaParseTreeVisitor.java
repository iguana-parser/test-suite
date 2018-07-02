package iguana;


import antlr4java.JavaParser;
import iguana.utils.input.Input;
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

            case "ImportDeclaration": {
                ImportDeclaration importDeclaration = ast.newImportDeclaration();
                return importDeclaration;
            }

            case "NormalClassDeclaration": {
                TypeDeclaration classDeclaration = ast.newTypeDeclaration();
                classDeclaration.modifiers().addAll(createList(node.getChildWithName("ClassModifier*").children()));
                classDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                classDeclaration.bodyDeclarations().addAll(createList(node.getChildWithName("ClassBody").childAt(1).children()));
                return classDeclaration;
            }

            case "EnumDeclaration": {
                EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
                enumDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                enumDeclaration.modifiers().addAll(createList(node.getChildWithName("ClassModifier*").children()));
                return enumDeclaration;
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

            case "FieldDeclaration": {
                List<VariableDeclarationFragment> fragments = getVariableDeclarationFragments(node.getChildWithName("VariableDeclarators"));
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

            case "InstanceInitializer": {
                Initializer initializer = ast.newInitializer();
                return initializer;
            }

            case "StaticInitializer": {
                Initializer initializer = ast.newInitializer();
                initializer.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.STATIC_KEYWORD));
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

            case "FieldModifier":
            case "InterfaceModifier":
            case "ConstantModifier":
            case "AbstractMethodModifier":
            case "ClassModifier": {
                if (node.hasChild("Annotation")) {
                    return null;
                } else {
                    return ast.newModifier(Modifier.ModifierKeyword.toKeyword(node.getText(input)));
                }
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

    private List<VariableDeclarationFragment> getVariableDeclarationFragments(ParseTreeNode node) {
        List<ParseTreeNode> variableDeclarators = node.childAt(0).getChildrenWithName("VariableDeclarator");
        List<VariableDeclarationFragment> fragments = new ArrayList<>();
        for (ParseTreeNode variableDeclarator : variableDeclarators) {
            VariableDeclarationFragment fragment = ast.newVariableDeclarationFragment();
            fragment.setName(getIdentifier(variableDeclarator.getChildWithName("VariableDeclaratorId").getChildWithName("Identifier")));
            fragments.add(fragment);
        }
        return fragments;
    }

}
