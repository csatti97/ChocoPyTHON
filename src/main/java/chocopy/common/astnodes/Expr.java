package chocopy.common.astnodes;

import chocopy.common.analysis.types.Type;
import com.fasterxml.jackson.annotation.JsonInclude;
import java_cup.runtime.ComplexSymbolFactory.Location;
import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 * Base of all AST nodes representing expressions.
 *
 * <p>There is nothing in this class, but there will be many AST node types that have fields that
 * are *any expression*. For those cases, having a field of this type will encompass all types of
 * expressions such as binary expressions and literals that subclass this class.
 */
public abstract class Expr extends Node {
    @JsonIgnore
    /** Code generation: this expression does not have a return register **/
    public boolean noreturn;

    /** A ChocoPy expression spanning source locations [LEFT..RIGHT]. */
    public Expr(Location left, Location right) {
        super(left, right);
        this.noreturn = false;
    }

    /**
     * The type of the value that this expression evaluates to.
     *
     * <p>This field is always null after the parsing stage, but is populated by the typechecker in
     * the semantic analysis stage.
     *
     * <p>After typechecking this field may be null only for expressions that cannot be assigned a
     * type. In particular, {@link NoneLiteral} expressions will not have a typed assigned to them.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Type inferredType;

    /** Sets getInferredType() to TYPE, returning TYPE. */
    public Type setInferredType(Type type) {
        inferredType = type;
        return type;
    }

    public Type getInferredType() {
        return inferredType;
    }
}
