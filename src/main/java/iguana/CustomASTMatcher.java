package iguana;

import org.eclipse.jdt.core.dom.*;

import java.util.Iterator;

public class CustomASTMatcher extends ASTMatcher {

    @Override
    public boolean match(FieldAccess node, Object other) {
        if (!(other instanceof FieldAccess || other instanceof QualifiedName)) {
            return false;
        }

        if (other instanceof FieldAccess) {
            FieldAccess o = (FieldAccess) other;
            return (
                    safeSubtreeMatch(node.getExpression(), o.getExpression())
                            && safeSubtreeMatch(node.getName(), o.getName()));
        } else {
            return node.toString().equals(other.toString());
        }
    }

    @Override
    public boolean match(QualifiedType node, Object other) {
        if (other instanceof SimpleType) {
            return node.toString().equals(other.toString());
        }
        return super.match(node, other);
    }

    @Override
    public boolean match(InfixExpression node, Object other) {
        if (!(other instanceof InfixExpression)) {
            return false;
        }

        return super.match(normalize(node), normalize((InfixExpression) other));
    }

    private static InfixExpression normalize(InfixExpression expr) {
        if (!expr.hasExtendedOperands()) return expr;

        AST ast = expr.getAST();

        InfixExpression result = ast.newInfixExpression();
        result.setLeftOperand((Expression) ASTNode.copySubtree(ast, expr.getLeftOperand()));
        result.setRightOperand((Expression) ASTNode.copySubtree(ast, expr.getRightOperand()));
        result.setOperator(expr.getOperator());

        for (Iterator<Expression> it = expr.extendedOperands().iterator(); it.hasNext(); ) {
            Expression extendedOperand = it.next();

            InfixExpression newExpr = ast.newInfixExpression();
            newExpr.setOperator(expr.getOperator());
            newExpr.setLeftOperand(result);
            newExpr.setRightOperand((Expression) ASTNode.copySubtree(ast, extendedOperand));
            result = newExpr;
        }

        return result;
    }

    /**
     * The max negative number is presented in the Eclipse AST as a number literal instead of a Prefix (unary minus)
     * expression.
     */
    public boolean match(PrefixExpression node, Object other) {
        if (other instanceof NumberLiteral) {
            if (((NumberLiteral) other).getToken().equals("-2147483648")) {
                return node.toString().equals("-2147483648");
            }
        }
        return super.match(node, other);
    }

}
