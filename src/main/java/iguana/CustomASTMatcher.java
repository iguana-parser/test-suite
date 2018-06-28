package iguana;

import org.eclipse.jdt.core.dom.*;

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

}
