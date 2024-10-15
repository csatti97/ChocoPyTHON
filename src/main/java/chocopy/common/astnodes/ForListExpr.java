package chocopy.common.astnodes;

import chocopy.common.analysis.NodeAnalyzer;
import java_cup.runtime.ComplexSymbolFactory.Location;

import java.util.List;

/** For-list, the list comprehension of generator*/
/** https://wiki.python.org/moin/Generators */
public final class ForListExpr extends Expr {
    /** Expr for each element with respect to control variable */
    public final Expr element;
    /** Control variable */
    public final Identifier identifier;
    /** Source of values of control statement. */
    public final Expr iterable;

    /** AST for `[ ELEMENTS ]` spanning source locations [LEFT..RIGHT]. */
    public ForListExpr(Location left, Location right, Expr elem, Identifier identifier, Expr iterable) {
        super(left, right);
        this.element = elem;
        this.identifier = identifier;
        this.iterable = iterable;
    }

    public <T> T dispatch(NodeAnalyzer<T> analyzer) {
        return analyzer.analyze(this);
    }
}
