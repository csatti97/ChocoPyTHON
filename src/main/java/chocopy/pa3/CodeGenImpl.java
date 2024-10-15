package chocopy.pa3;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;
import chocopy.common.codegen.*;

import java.util.List;

import static chocopy.common.codegen.RiscVBackend.Register.*;

/**
 * The main location for PA3 implementation.
 *
 * <p>A large part of the functionality has already been implemented in the base class, CodeGenBase.
 * You are likely to use many of its fields and utility methods.
 *
 * <p>Also read the PDF spec for details on what the base class does and what APIs it exposes for
 * its subclass (this one). Of particular importance is knowing what all the SymbolInfo classes
 * contain.
 */
public class CodeGenImpl extends CodeGenBase {

    /** A code generator emitting instructions to BACKEND. */
    public CodeGenImpl(RiscVBackend backend) {
        super(backend);
    }

    /** Operation on None. */
    private final Label errorNone = new Label("error.None");
    /** Division by zero. */
    private final Label errorDiv = new Label("error.Div");
    /** Index out of bounds. */
    private final Label errorOob = new Label("error.OOB");

    /**
     * Emits the top level of the program.
     *
     * <p>This method is invoked exactly once, and is surrounded by some boilerplate code that: (1)
     * initializes the heap before the top-level begins and (2) exits after the top-level ends.
     *
     * <p>You only need to generate code for statements.
     *
     * @param statements top level statements
     */
    protected void emitTopLevel(List<Stmt> statements) {
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(null);
        backend.emitADDI(
                SP, SP, -2 * backend.getWordSize(), "Saved FP and saved RA (unused at top level).");
        backend.emitSW(ZERO, SP, 0, "Top saved FP is 0.");
        backend.emitSW(ZERO, SP, 4, "Top saved RA is 0.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(), "Set FP to previous SP.");

        for (Stmt stmt : statements) {
            stmt.dispatch(stmtAnalyzer);
        }
        backend.emitLI(A0, EXIT_ECALL, "Code for ecall: exit");
        backend.emitEcall(null);
    }

    /**
     * Emits the code for a function described by FUNCINFO.
     *
     * <p>This method is invoked once per function and method definition. At the code generation
     * stage, nested functions are emitted as separate functions of their own. So if function `bar`
     * is nested within function `foo`, you only emit `foo`'s code for `foo` and only emit `bar`'s
     * code for `bar`.
     */
    protected void emitUserDefinedFunction(FuncInfo funcInfo) {
        backend.emitGlobalLabel(funcInfo.getCodeLabel());
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(funcInfo);

        for (Stmt stmt : funcInfo.getStatements()) {
            stmt.dispatch(stmtAnalyzer);
        }

        backend.emitMV(A0, ZERO, "Returning None implicitly");

        backend.emitLocalLabel(stmtAnalyzer.epilogue, "Epilogue");
        // TODO: Implement epilogue (reset fp, and so on).

        backend.emitJR(RA, "Return to caller");
    }

    /** An analyzer that encapsulates code generation for statements. */
    private class StmtAnalyzer extends AbstractNodeAnalyzer<Void> {
        /*
         * The symbol table has all the info you need to determine
         * what a given identifier 'x' in the current scope is. You can
         * use it as follows:
         *   SymbolInfo x = sym.get("x");
         *
         * A SymbolInfo can be one of the following:
         * - ClassInfo: a descriptor for classes
         * - FuncInfo: a descriptor for functions/methods
         * - AttrInfo: a descriptor for attributes
         * - GlobalVarInfo: a descriptor for global variables
         * - StackVarInfo: a descriptor for variables allocated on the stack,
         *      such as locals and parameters
         *
         * Since the input program is assumed to be semantically
         * valid and well-typed at this stage, you can always assume that
         * the symbol table contains valid information. For example, in
         * an expression `foo()` you know that sym.get("foo") will either be
         * a FuncInfo or ClassInfo, but not any of the other infos
         * and never null.
         *
         * The symbol table in funcInfo has already been populated in
         * the base class: CodeGenBase, so you can query it freely.
         *
         * The symbol table also maps nonlocal and global vars, so you
         * only need to look up one symbol table, and it will fetch the
         * appropriate info for the var that is currently in scope.
         */

        /** Symbol table for statements. */
        private final SymbolTable<SymbolInfo> sym;

        /** Label of code that exits from procedure. */
        protected final Label epilogue;

        /** The descriptor for the current function, or null at the top level. */
        private final FuncInfo funcInfo;

        /** An analyzer for the function described by FUNCINFO0, which is null for the top level. */
        StmtAnalyzer(FuncInfo funcInfo0) {
            funcInfo = funcInfo0;
            if (funcInfo == null) {
                sym = globalSymbols;
            } else {
                sym = funcInfo.getSymbolTable();
            }
            epilogue = generateLocalLabel();
        }

        // TODO: Example analysis that (incorrectly) emits a no-op.
        @Override
        public Void analyze(ReturnStmt stmt) {
            backend.emitMV(ZERO, ZERO, "No-op");
            return null;
        }
    }

    /**
     * Emits custom code in the CODE segment.
     *
     * <p>This method is called after emitting the top level and the function bodies for each
     * function.
     *
     * <p>You can use this method to emit anything you want outside the top level or functions, e.g.
     * custom routines that you may want to call from within your code to do common tasks.
     *
     * <p>To start you off, here is an implementation of three routines that will be commonly needed
     * from within the code you will generate for statements.
     *
     * <p>The routines are error handlers for operations on None, index out of bounds, and division
     * by zero. They never return to their caller. Just jump to one of these routines to throw an
     * error and exit the program. For example, to throw an OOB error: backend.emitJ(errorOob, "Go
     * to out-of-bounds error and abort");
     */
    protected void emitCustomCode() {
        emitErrorFunc(errorNone, "Operation on None");
        emitErrorFunc(errorDiv, "Division by zero");
        emitErrorFunc(errorOob, "Index out of bounds");
    }

    /** Emits an error routine labeled ERRLABEL that aborts with message MSG. */
    private void emitErrorFunc(Label errLabel, String msg) {
        backend.emitGlobalLabel(errLabel);
        backend.emitLI(A0, ERROR_NONE, "Exit code for: " + msg);
        backend.emitLA(A1, constants.getStrConstant(msg), "Load error message as str");
        backend.emitADDI(
                A1, A1, getAttrOffset(strClass, "__str__"), "Load address of attribute __str__");
        backend.emitJ(abortLabel, "Abort");
    }
}
