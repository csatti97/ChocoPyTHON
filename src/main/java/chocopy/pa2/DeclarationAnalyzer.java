package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.VarDef;

/** Analyzes declarations to create a top-level symbol table. */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {
    /** Current symbol table. Changes with new declarative region. */
    private final SymbolTable<Type> sym = new SymbolTable<>();
    /** Global symbol table. */
    private final SymbolTable<Type> globals = sym;
    /** Receiver for semantic error messages. */
    private final Errors errors;

    /** A new declaration analyzer sending errors to ERRORS0. */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
    }

    public SymbolTable<Type> getGlobals() {
        return globals;
    }

    // TODO: Analysis scaffold that (incorrectly) ignores all declarations.
    @Override
    public Type analyze(Program program) {
        for (Declaration decl : program.declarations) {
            // Dispatch to the right analyzer; populate to symbol table; handle conflicts.
        }

        return null;
    }

    @Override
    public Type analyze(VarDef varDef) {
        return ValueType.annotationToValueType(varDef.var.type);
    }
}
