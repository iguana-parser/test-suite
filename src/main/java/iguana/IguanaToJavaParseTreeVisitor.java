package iguana;


import iguana.utils.input.Input;
import org.eclipse.jdt.core.dom.*;
import org.iguana.parsetree.*;

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
                TypeDeclaration typeDeclaration = ast.newTypeDeclaration();
                typeDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                return typeDeclaration;
            }

            case "EnumDeclaration": {
                EnumDeclaration enumDeclaration = ast.newEnumDeclaration();
                enumDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                return enumDeclaration;
            }

            case "NormalInterfaceDeclaration": {
                TypeDeclaration interfaceDeclaration = ast.newTypeDeclaration();
                interfaceDeclaration.setInterface(true);
                interfaceDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                return interfaceDeclaration;
            }

            case "AnnotationTypeDeclaration": {
                AnnotationTypeDeclaration annotationTypeDeclaration = ast.newAnnotationTypeDeclaration();
                annotationTypeDeclaration.setName(getIdentifier(node.getChildWithName("Identifier")));
                return annotationTypeDeclaration;
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


}
