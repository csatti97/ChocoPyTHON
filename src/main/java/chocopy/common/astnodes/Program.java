package chocopy.common.astnodes;

import chocopy.common.analysis.NodeAnalyzer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java_cup.runtime.ComplexSymbolFactory.Location;

import java.util.ArrayList;
import java.util.List;

/** An entire ChocoPy program. */
public class Program extends Node {
    /** Initial variable, class, and function declarations. */
    public final List<Declaration> declarations;
    /** Trailing statements. */
    public final List<Stmt> statements;
    /** Accumulated errors. */
    public final Errors errors;

    /**
     * AST for `DECLARATIONS STATEMENTS` spanning source locations [LEFT..RIGHT].
     *
     * <p>ERRORS is the container for all error messages applying to the program.
     */
    public Program(
            Location left,
            Location right,
            List<Declaration> declarations,
            List<Stmt> statements,
            Errors errors) {
        super(left, right);
        this.declarations = declarations;
        this.statements = statements;
        if (errors == null) {
            this.errors = new Errors(new ArrayList<>());
        } else {
            this.errors = errors;
        }
    }

    public <T> T dispatch(NodeAnalyzer<T> analyzer) {
        return analyzer.analyze(this);
    }

    /** Returns true iff there is at least one error in the program. */
    @JsonIgnore
    public boolean hasErrors() {
        return errors.hasErrors();
    }

    /** A convenience method returning the list of all CompilerErrors for this program. */
    @JsonIgnore
    public List<CompilerError> getErrorList() {
        return errors.errors;
    }
}
