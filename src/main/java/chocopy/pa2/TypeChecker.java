package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.*;

import static chocopy.common.analysis.types.Type.INT_TYPE;
import static chocopy.common.analysis.types.Type.OBJECT_TYPE;

/**
 * Analyzer that performs ChocoPy type checks on all nodes. Applied after collecting declarations.
 */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {
    /** The current symbol table (changes depending on the function being analyzed). */
    private final SymbolTable<Type> sym;
    /** Collector for errors. */
    private final Errors errors;

    /**
     * Creates a type checker using GLOBALSYMBOLS for the initial global symbol table and ERRORS0 to
     * receive semantic errors.
     */
    public TypeChecker(SymbolTable<Type> globalSymbols, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
    }

    /**
     * Inserts an error message in NODE if there isn't one already. The message is constructed with
     * MESSAGE and ARGS as for String.format.
     */
    private void err(Node node, String message, Object... args) {
        errors.semError(node, message, args);
    }

    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public Type analyze(IntegerLiteral i) {
        return i.setInferredType(Type.INT_TYPE);
    }

    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "-":
            case "*":
            case "//":
            case "%":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(INT_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }

    // TODO: Example analysis that (incorrectly) assign an empty type to all identifiers.
    @Override
    public Type analyze(Identifier id) {
        return id.setInferredType(ValueType.EMPTY_TYPE);
    }
}
