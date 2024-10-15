package chocopy.pa3;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.*;
import chocopy.common.codegen.*;
import chocopy.common.analysis.types.*;

import static chocopy.common.Utils.*;

import java.util.List;
import java.util.Stack;

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
    public final Label errorArg = new Label("error.Arg");
    /** Operation on None. */
    public final Label errorNone = new Label("error.None");
    /** Division by zero. */
    public final Label errorDiv = new Label("error.Div");
    /** Index out of bounds. */
    public final Label errorOob = new Label("error.OOB");
    /** make boolean. */
    public final Label makebool = new Label("makebool");
    /** make int. */
    public final Label makeint = new Label("makeint");
    /** construct list */
    public final Label conslist = new Label("conslist");
    /** initialize one-char strings */
    public final Label initchars = new Label("initchars");
    /** interning one-char string for all chars (ascii 0-255) */
    public final Label allChars = new Label("allChars");
    /** string concatenation **/
    public final Label strcat = new Label("strcat");
    /** string `==` **/
    public final Label streql = new Label("streql");
    /** string `!=` **/
    public final Label strneql = new Label("strneql");

    /** the branch label used for indexExpr **/
    public int indexlabelCnt = 0;

    /** when there is not index to string, avoid interning to save cost **/
    public boolean requireInitChar = false;

    public Constants getConstants(){
        return constants;
    }


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
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(this, null);
        backend.emitADDI(
                SP, SP, -2 * backend.getWordSize(), "Saved FP and saved RA (unused at top level).");
        backend.emitSW(ZERO, SP, 0, "Top saved FP is 0.");
        backend.emitSW(ZERO, SP, backend.getWordSize(), "Top saved RA is 0.");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(), "Set FP to previous SP.");

        backend.emitInsn(String.format("li %s, %s", T0, "@requireInitChar"), "Check whether we need interning");
        backend.emitBEQZ(T0, new Label("noinitchar"), "If no indexExpr of string, omit");
        backend.emitJAL(initchars, "Initialize one-character strings.");
        backend.emitLocalLabel(new Label("noinitchar"), "Jump here if no initchars");

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
        // store the states before we start analyzing the body of the function
        backend.emitADDI(
                SP, SP, -2 * backend.getWordSize(), "Saved FP and saved RA (unused at top level).");
        backend.emitSW(RA, SP, 0, "return address");
        backend.emitSW(FP, SP, backend.getWordSize(), "control link");
        backend.emitADDI(FP, SP, 2 * backend.getWordSize(), "Set FP to previous SP.");

        backend.emitADDI(
                SP, SP, -funcInfo.getLocals().size() * backend.getWordSize(), "Move $sp for locals.");

        // Initialize local variables in memory
        for (StackVarInfo svi : funcInfo.getLocals()) {
            // TODO: a more general method is needed
            if (svi.getVarType().equals(Type.INT_TYPE)) {
                int imm = ((IntegerLiteral) svi.getInitialValue()).value;
                backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), imm));
            } else if (svi.getVarType().equals(Type.BOOL_TYPE)){
                boolean value = ((BooleanLiteral) svi.getInitialValue()).value;
                int imm = value ? 1 : 0;
                backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), value));
            } else if (svi.getVarType().equals(Type.STR_TYPE)){
                String str = ((StringLiteral) svi.getInitialValue()).value;
                backend.emitLA(A0, constants.getStrConstant(str), "Load string literal");
            } else {
                backend.emitMV(A0, ZERO, String.format("Load None"));
            }
            backend.emitSW(A0, FP, -(funcInfo.getVarIndex(svi.getVarName()) + 1) * backend.getWordSize(), "local variable");
        }
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(this, funcInfo);

        backend.emitGlobalLabel(funcInfo.getCodeStartLabel());
        for (Stmt stmt : funcInfo.getStatements()) {
            stmt.dispatch(stmtAnalyzer);
        }

        backend.emitMV(A0, ZERO, "Returning None implicitly");

        backend.emitLocalLabel(stmtAnalyzer.epilogue, "Epilogue");
        // Implement epilogue
        backend.emitLW(RA, FP, -2 * backend.getWordSize(), "Get return address");
        backend.emitLW(FP, FP, -1 * backend.getWordSize(), "Use control link to restore caller's fp");
        backend.emitADDI(
                        SP, SP, 
                        2 * backend.getWordSize() + 
                        funcInfo.getLocals().size() * backend.getWordSize(), 
                        "Recover SP."
        );
        backend.emitJR(RA, "Return to caller");
    }

    /** An analyzer that encapsulates code generation for statements. */
    public class StmtAnalyzer extends AbstractNodeAnalyzer<Void> {
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

        /** Get corresponding CodeGenImpl **/
        private CodeGenImpl codegen;

        /** Symbol table for statements. */
        private final SymbolTable<SymbolInfo> sym;

        /** Label of code that exits from procedure. */
        protected final Label epilogue;

        /** The descriptor for the current function, or null at the top level. */
        private final FuncInfo funcInfo;

        /** The flag that whether a `self` in a method is not assigned by some statement.**/
        /** Trivial in normal function */
        public boolean isSelfConstant;

        private int nextFreeTempReg = 0;
        private int usedRegNum = 0;

        Stack<Boolean> regTrivialStk = new Stack<Boolean>();

        /** An analyzer for the function described by FUNCINFO0, which is null for the top level. */
        StmtAnalyzer(CodeGenImpl codegen0, FuncInfo funcInfo0) {
            codegen = codegen0;
            funcInfo = funcInfo0;
            isSelfConstant = true;
            if (funcInfo == null) {
                sym = globalSymbols;
            } else {
                sym = funcInfo.getSymbolTable();
            }
            epilogue = generateLocalLabel();
        }

        public RiscVBackend.Register getFreeReg() {
            int retRegIdx = nextFreeTempReg;
            usedRegNum += 1;
            nextFreeTempReg = (nextFreeTempReg + 1) % 7; // 7 temp reg. in all

            // if register used up, pick LRU register in a ring buffer policy
            if (usedRegNum > 7) {
                // push returned reg on stack (because it has been occupied)
                if (!backend.TempRegs[retRegIdx].isValueTrivial)
                {
                    backend.emitADDI(
                            SP, SP, -1 * backend.getWordSize(), "Push one argument (move $sp first).");
                    backend.emitSW(backend.TempRegs[retRegIdx], SP, 0, 
                        "Push argument (getfreereg): " + backend.TempRegs[retRegIdx]);
                }


                // regisiter value is trivial initially
                regTrivialStk.push(backend.TempRegs[retRegIdx].isValueTrivial);

                backend.TempRegs[retRegIdx].isValueTrivial = true;
                return backend.TempRegs[retRegIdx];
            }


            backend.TempRegs[retRegIdx].isValueTrivial = true;
            return backend.TempRegs[retRegIdx];
        }

        public RiscVBackend.Register getReturnReg(Expr expr) {
            // Top-level statement does not have return register
            if (usedRegNum == 0 || expr.noreturn)
            {
                // System.out.println("[StmtAnalyzer] Fail to get return register!");
                return ZERO;
            }
            int retRegIdx = nextFreeTempReg - 1;
            if (retRegIdx < 0)
            {
                retRegIdx = 6;
            }

            return backend.TempRegs[retRegIdx];
        }

        public Void SaveBusyReg() {
            if (nextFreeTempReg == 0)
            {
                return null;
            }
            backend.emitADDI(
                        SP, SP, -nextFreeTempReg * backend.getWordSize(), "Push registers before function call.");

            for (int i = 0; i < nextFreeTempReg; i++)
            {
                backend.emitSW(backend.TempRegs[i], SP, i * backend.getWordSize(), "Save " + backend.TempRegs[i]);
            }

            return null;
        }

        public Void LoadBusyReg() {
            if (nextFreeTempReg == 0)
            {
                return null;
            }
            for (int i = 0; i < nextFreeTempReg; i++)
            {
                backend.emitLW(backend.TempRegs[i], SP, i * backend.getWordSize(), "Load " + backend.TempRegs[i]);
            }
            backend.emitADDI(
                        SP, SP, nextFreeTempReg * backend.getWordSize(), "Pop registers after function call.");
            
            return null;
        }

        public Void FreeReg(int cnt) {
            if (usedRegNum == 0) {
                System.out.println("[StmtAnalyzer] Illegal free reg. No reg. in used!");
                return null;
            }
            for (int i = 0; i < cnt; i++)
            {
                nextFreeTempReg = nextFreeTempReg - 1;
                if (nextFreeTempReg < 0)
                {
                    nextFreeTempReg = 6;
                }
                usedRegNum -= 1;
                if (usedRegNum > 6) {
                    boolean isTrivial = regTrivialStk.pop();
                    if (!isTrivial)
                    {
                        backend.emitLW(backend.TempRegs[nextFreeTempReg], SP, 0, "Pop one argument(free reg.).");
                        backend.emitADDI(
                                SP, SP, 1 * backend.getWordSize(), "Pop one argument (move $sp).");
                    }

                }
            }
            return null;
        }

        public Void ForceFreeAllReg()
        {
            if (usedRegNum > 0)
            {
                backend.emitADDI(
                            SP, SP, usedRegNum * backend.getWordSize(), "Pop all register.");
            }
            return null;
        }

        @Override
        public Void analyze(ExprStmt stmt) {
            stmt.expr.noreturn = true;
            stmt.expr.dispatch(this);
            return null;
        }

        @Override
        public Void analyze(CallExpr expr) {
            backend.emitComment(String.format("CallExpr: %s", expr.function.name));
            if (expr.function.name.equals("print"))
            {
                CodeGenStdFunc.analyzePrint(backend, codegen, this, expr);
            } else if (expr.function.name.equals("len")){
                CodeGenStdFunc.analyzeLen(backend, codegen, this, expr);
            } else if (expr.function.name.equals("input")) {
                CodeGenStdFunc.analyzeInput(backend, codegen, this, expr);
            } else if (expr.function.name.equals("int")) {
                CodeGenStdFunc.analyzeInt(backend, codegen, this, expr);
            } else if (expr.function.name.equals("bool")) {
                CodeGenStdFunc.analyzeBool(backend, codegen, this, expr);
            } else if (expr.function.name.equals("str")) {
                CodeGenStdFunc.analyzeStr(backend, codegen, this, expr);
            } else if (sym.get(expr.function.name) instanceof ClassInfo){
                analyzeClass(expr);
            } else {
                analyzeFunctionCall(expr);
            }

            return null;
        }

        public Void analyze(MethodCallExpr expr) {
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register objReg = getFreeReg();
            Label notNoneLabel = generateLocalLabel();
            ClassValueType objType = (ClassValueType) expr.method.object.getInferredType();
            ClassInfo objClassInfo = (ClassInfo) sym.get(objType.className());

            // load the object
            expr.method.object.dispatch(this);

            backend.emitBNEZ(objReg, notNoneLabel, "Ensure not None");
            backend.emitJ(errorNone, "Go to error handler");

            backend.emitLocalLabel(notNoneLabel, "Not None");

            RiscVBackend.Register funcReg = getFreeReg();
            int methodIndex = objClassInfo.getMethodIndex(expr.method.member.name);
            String funcName = expr.method.member.name;

            // follow the dispatch table
            backend.emitLW(funcReg, objReg, 8, "Get dispatch table of " + objType);
            backend.emitLW(funcReg, funcReg,
                    methodIndex * backend.getWordSize(),
                    "Get class method: " + objType + "." + funcName
            );
            // Remark: 8 bytes is the offset for the dispatch table

            SaveBusyReg(); // save for function call

            FuncInfo callfuncInfo = (FuncInfo) objClassInfo.methods.get(methodIndex);

            RiscVBackend.Register valReg = getFreeReg();

            if (callfuncInfo == null)
            {
                System.out.println(objType.className() + "." + funcName);
                System.out.println("[analyzeMethodCall] FuncInfo not exists!");
                return null;
            }

            int stackSize = expr.args.size() + 1; // stack size in words
            // Remark: by default, the first args. is self. That's why `+1` here.

            // push frame pointers of parent/self functions
            // top-level invocation does not require frame push
            if (funcInfo != null)
            {
                int depth = callfuncInfo.getDepth();
                stackSize += depth;

                backend.emitADDI(SP, SP, -stackSize * backend.getWordSize(), "Push arguments for func.");

                FuncInfo pushedInfo = funcInfo;
                int nowPushedDepth = funcInfo.getDepth();
                int nowDepth = funcInfo.getDepth();
                int offset_caller = funcInfo.getParams().size();
                int offset_callee = expr.args.size();

                // push frames (static links)
                // Remark: outter frame are put on higher over the frame
                while (nowPushedDepth >= 0) {
                    if (nowPushedDepth < depth)
                    {
                        if (nowPushedDepth == nowDepth)
                        {
                            backend.emitSW(FP, SP, offset_callee * backend.getWordSize(),"Push & Get static link to " + pushedInfo.getFuncName());
                        } else {
                            backend.emitLW(valReg, FP, offset_caller * backend.getWordSize(), "Get static link to " + pushedInfo.getFuncName());
                            backend.emitSW(valReg, SP, offset_callee * backend.getWordSize(), "Push on stack");
                        }
                        offset_callee++;
                    }

                    // frame same as the caller
                    if (nowPushedDepth != nowDepth)
                    {
                        offset_caller++;
                    }
                    pushedInfo = pushedInfo.getParentFuncInfo();
                    nowPushedDepth--;
                }
            } else {
                backend.emitADDI(SP, SP, -stackSize * backend.getWordSize(), "Push arguments for func.");
            }

            // push arguments
            backend.emitSW(objReg, SP, 0, String.format("Push self"));

            for (Expr arg: expr.args){
                arg.dispatch(this);
                int idx =  expr.args.indexOf(arg) + 1; // +1 for self here

                backend.emitSW(valReg, SP, idx * backend.getWordSize(), String.format("Push argument %s from last", idx));
            }

            FreeReg(1); // free valReg

            // Move SP to the last argument
            backend.emitJALR(funcReg, "Invoke function: "+funcName);
            backend.emitADDI(SP, SP, stackSize * backend.getWordSize(), "Pop arguments for func.");
            LoadBusyReg();

            backend.emitMV(retReg, A0, "Move returned value.");

            FreeReg(2); // free objReg, valReg

            return null;
        }

        public Void analyzeClass(CallExpr expr)
        {
            ClassInfo clsinfo = (ClassInfo) sym.get(expr.function.name);
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register initReg = getFreeReg();

            backend.emitLA(A0, clsinfo.getPrototypeLabel(), "Load pointer to prototype of: " + clsinfo.getClassName());
            backend.emitJAL(new Label("alloc"), "Allocate new object in A0");
            backend.emitMV(retReg, A0, "Move returned value to target register");

            backend.emitLW(A1, A0, 8, "Load address of object's dispatch table");
            backend.emitLW(initReg, A1, 0, "Load address of method: " + clsinfo.getClassName() + ".__init__");

            
            int stackSize = expr.args.size() + 1; // stack size in words

            // push registers before pushing arguments
            SaveBusyReg();

            // construct should push itself as the first argument
            backend.emitADDI(SP, SP, -stackSize * backend.getWordSize(), "Push arguments for func.");

            // push self as the first argument
            backend.emitSW(A0, SP, 0, String.format("Push argument self from last"));

            // push arguments
            RiscVBackend.Register valReg = getFreeReg();
            for (Expr arg: expr.args){
                arg.dispatch(this);
                int idx =  expr.args.indexOf(arg) + 1;

                backend.emitSW(valReg, SP, idx * backend.getWordSize(), String.format("Push argument %s", idx));
            }
            FreeReg(1); // free ValReg

            // Call the method
            backend.emitJALR(initReg, "Invoke method: "  + clsinfo.getClassName() + ".__init__");
            backend.emitADDI(SP, SP, stackSize * backend.getWordSize(), "Pop arguments for func.");
            
            LoadBusyReg();
            FreeReg(1); // free initReg
            
            return null;
        }

        @Override
        public Void analyze(NoneLiteral literal) {
            RiscVBackend.Register retReg = getReturnReg(literal);
            backend.emitMV(retReg, ZERO, "Load None");

            return null;
        }

        public Void analyzeMemberExprAddr(MemberExpr expr)
        {
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register objReg = getFreeReg();
            Label notNoneLabel = generateLocalLabel();
            ClassValueType objType = (ClassValueType) expr.object.getInferredType();
            ClassInfo objClassInfo = (ClassInfo) sym.get(objType.className());
            boolean omitNoneCheck = false;

            expr.object.dispatch(this);

            // optimization: if the memberexpr's object is `self` in method.
            // we omit the check of None if there is no assignment to self in this method.
            if (funcInfo != null)
            {
                if (expr.object instanceof Identifier)
                {
                    Identifier idobj = (Identifier) expr.object;
                    String idname = idobj.name;

                    // check whether the id is the `self`
                    if (funcInfo.isMethod && funcInfo.getParams().indexOf(idname) == 0)
                    {
                        omitNoneCheck = isSelfConstant;
                    }
                }
            }

            if (!omitNoneCheck)
            {
                backend.emitBNEZ(objReg, notNoneLabel, "Ensure not None");
                backend.emitJ(errorNone, "Go to error handler");

                backend.emitLocalLabel(notNoneLabel, "Not None"); 
            }

            backend.emitADDI(retReg, objReg,
                    12 + objClassInfo.getAttributeIndex(expr.member.name) * backend.getWordSize(),
                    "Get attribute addr: " + objType + "." + expr.member.name
            );

            FreeReg(1);
            return null;
        }

        @Override
        public Void analyze(MemberExpr expr) {
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register objReg = getFreeReg();
            Label notNoneLabel = generateLocalLabel();
            ClassValueType objType = (ClassValueType) expr.object.getInferredType();
            ClassInfo objClassInfo = (ClassInfo) sym.get(objType.className());
            boolean omitNoneCheck = false;

            expr.object.dispatch(this);

            // optimization: if the memberexpr's object is `self` in method.
            // we omit the check of None if there is no assignment to self in this method.
            if (funcInfo != null)
            {
                if (expr.object instanceof Identifier)
                {
                    Identifier idobj = (Identifier) expr.object;
                    String idname = idobj.name;

                    // check whether the id is the `self`
                    if (funcInfo.isMethod && funcInfo.getParams().indexOf(idname) == 0)
                    {
                        omitNoneCheck = isSelfConstant;
                    }
                }
            }

            if (!omitNoneCheck)
            {
                backend.emitBNEZ(objReg, notNoneLabel, "Ensure not None");
                backend.emitJ(errorNone, "Go to error handler");

                backend.emitLocalLabel(notNoneLabel, "Not None");
            }

            backend.emitLW(retReg, objReg,
                    12 + objClassInfo.getAttributeIndex(expr.member.name) * backend.getWordSize(),
                    "Get attribute: " + objType + "." + expr.member.name
            );
            // Remark: 12 bytes is the offset for the attrtibutes

            FreeReg(1);
            return null;
        }

        @Override
        public Void analyze(IndexExpr expr) {
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register listReg = getFreeReg();
            expr.list.dispatch(this);
            RiscVBackend.Register indexReg = getFreeReg();
            expr.index.dispatch(this);
            RiscVBackend.Register tmpReg = getFreeReg();
            indexlabelCnt += 1;

            Label noneCheckLabel = new Label(String.format("index_none_%d", indexlabelCnt));
            Label oobCheckLabel = new Label(String.format("index_OOB_%d", indexlabelCnt));

            backend.emitComment("IndexExpr Start");

            if (expr.list.getInferredType() instanceof ListValueType)
            {
                // check whether list is None
                backend.emitBNEZ(listReg, noneCheckLabel,"Ensure not None");
                backend.emitJ(errorNone, "Go to error handler");
                backend.emitLocalLabel(noneCheckLabel, "If list is not None, go ahead");

                // check whether index is in bound
                backend.emitLW(tmpReg, listReg, "@.__len__","Load attribute: __len__");
                backend.emitBLTU(indexReg, tmpReg, oobCheckLabel, "Ensure 0 <= index < len");
                backend.emitJ(errorOob, "Go to error handler");
                backend.emitLocalLabel(oobCheckLabel, "If listindex is not OOB, go ahead");

                // compute the address of the element and return
                backend.emitADDI(indexReg, indexReg, 4, "Compute list element offset in words");
                backend.emitLI(retReg, 4, "Word size in bytes");
                backend.emitMUL(indexReg, indexReg, retReg, "Compute list element offset in bytes");
                backend.emitADD(indexReg, listReg, indexReg, "Pointer to list element");
                backend.emitLW(indexReg, indexReg, 0, "Get list element");
                backend.emitMV(retReg, indexReg, "Move returned value to " + retReg);
            } else if (expr.list.getInferredType().equals(Type.STR_TYPE)){
                requireInitChar = true; // require interning initialization when indexing to str

                // check whether index is in bound
                backend.emitLW(tmpReg, listReg, "@.__len__","Load attribute: __len__");
                backend.emitBLTU(indexReg, tmpReg, oobCheckLabel, "Ensure 0 <= index < len");
                backend.emitJ(errorOob, "Go to error handler");
                backend.emitLocalLabel(oobCheckLabel, "If listindex is not OOB, go ahead");

                backend.emitADDI(indexReg, indexReg, "@.__elts__", "Convert index to offset to char in bytes");
                backend.emitADD(indexReg, listReg, indexReg, "Get pointer to char");
                backend.emitLBU(indexReg, indexReg, 0, "Load character");

                backend.emitComment(
                    "Get one-char string from the interning table(allChars), compute the offset"
                );
                backend.emitLI(tmpReg, 20, "size of string, 5 words");
                backend.emitMUL(indexReg, indexReg, tmpReg, "Multiply by size of string object");
                backend.emitLA(retReg, allChars, "Index into first string in single-char table");
                backend.emitADD(retReg, retReg, indexReg, "Offset to our char");

            } else {
                System.out.println("[IndexExpr] Index object not support!");
            }


            FreeReg(3);

            backend.emitComment("IndexExpr End");

            return null;
        }

        public Void analyzeIndexAddr(IndexExpr expr) {
            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register listReg = getFreeReg();
            expr.list.dispatch(this);
            RiscVBackend.Register indexReg = getFreeReg();
            expr.index.dispatch(this);
            RiscVBackend.Register tmpReg = getFreeReg();
            indexlabelCnt += 1;

            Label noneCheckLabel = new Label(String.format("index_none_%d", indexlabelCnt));
            Label oobCheckLabel = new Label(String.format("index_OOB_%d", indexlabelCnt));

            backend.emitComment("IndexExpr Get Addr. Start");

            // check whether list is None
            backend.emitBNEZ(listReg, noneCheckLabel,"Ensure not None");
            backend.emitJ(errorNone, "Go to error handler");
            backend.emitLocalLabel(noneCheckLabel, "If list is not None, go ahead");

            // check whether index is in bound
            backend.emitLW(tmpReg, listReg, "@.__len__","Load attribute: __len__");
            backend.emitBLTU(indexReg, tmpReg, oobCheckLabel, "Ensure 0 <= index < len");
            backend.emitJ(errorOob, "Go to error handler");
            backend.emitLocalLabel(oobCheckLabel, "If listindex is not OOB, go ahead");

            // compute the address of the element and return

            backend.emitADDI(indexReg, indexReg, 4, "Compute list element offset in words");
            backend.emitLI(retReg, 4, "Word size in bytes");
            backend.emitMUL(indexReg, indexReg, retReg, "Compute list element offset in bytes");
            backend.emitADD(indexReg, listReg, indexReg, "Pointer to list element");
            backend.emitMV(retReg, indexReg, "Move returned value to " + retReg);

            FreeReg(3);

            backend.emitComment("IndexExpr Get Addr. End");
            return null;
        }

        public Void analyzeFunctionCall(CallExpr expr){
            FuncInfo callfuncInfo = (FuncInfo) sym.get(expr.function.name);
            String funcName = expr.function.name;
            SaveBusyReg();
            RiscVBackend.Register valReg = getFreeReg();

            if (callfuncInfo == null)
            {
                System.out.println("[analyzeFunctionCall] FuncInfo not exists!");
                return null;
            }

            int stackSize = expr.args.size(); // stack size in words

            // push frame pointers of parent/self functions
            // top-level invocation does not require frame push
            if (funcInfo != null)
            {
                int depth = callfuncInfo.getDepth();
                stackSize += depth;

                backend.emitADDI(SP, SP, -stackSize * backend.getWordSize(), "Push arguments for func.");

                FuncInfo pushedInfo = funcInfo;
                int nowPushedDepth = funcInfo.getDepth();
                int nowDepth = funcInfo.getDepth();
                int offset_caller = funcInfo.getParams().size();
                int offset_callee = expr.args.size();

                // push frames (static links)
                // Remark: outter frame are put on higher over the frame
                while (nowPushedDepth >= 0) {
                    if (nowPushedDepth < depth)
                    {
                        if (nowPushedDepth == nowDepth)
                        {
                            backend.emitSW(FP, SP, offset_callee * backend.getWordSize(),"Push & Get static link to " + pushedInfo.getFuncName());
                        } else {
                            backend.emitLW(valReg, FP, offset_caller * backend.getWordSize(), "Get static link to " + pushedInfo.getFuncName());
                            backend.emitSW(valReg, SP, offset_callee * backend.getWordSize(), "Push on stack");
                        }
                        offset_callee++;
                    }

                    // frame same as the caller
                    if (nowPushedDepth != nowDepth)
                    {
                        offset_caller++;
                    }
                    pushedInfo = pushedInfo.getParentFuncInfo();
                    nowPushedDepth--;
                }
            } else {
                backend.emitADDI(SP, SP, -stackSize * backend.getWordSize(), "Push arguments for func.");
            }

            // push arguments
            for (Expr arg: expr.args){
                arg.dispatch(this);
                int idx =  expr.args.indexOf(arg);

                backend.emitSW(valReg, SP, idx * backend.getWordSize(), String.format("Push argument %s from last", idx));
            }

            FreeReg(1); // Free ValReg

            // Move SP to the last argument
            backend.emitJAL(callfuncInfo.getCodeLabel(), "Invoke function: "+funcName);
            backend.emitADDI(SP, SP, stackSize * backend.getWordSize(), "Pop arguments for func.");
            LoadBusyReg();

            backend.emitMV(getReturnReg(expr), A0, "Move returned value.");

            return null;
        }

        @Override
        public Void analyze(AssignStmt stmt)
        {
            Type assignValueType = stmt.value.getInferredType();
            RiscVBackend.Register valReg = getFreeReg();
            stmt.value.dispatch(this);
            RiscVBackend.Register tgtAddrReg = getFreeReg();

            String commentAssign;
            Type targetType;

            for (int idx = 0; idx < stmt.targets.size(); idx++) {
                Expr target = stmt.targets.get(idx);

                if (target instanceof Identifier) {
                    Identifier tid = (Identifier) target;
                    analyzeIDAddr(tid);
                    commentAssign = String.format("Assign to variable: %s", tid.name);
                    targetType = tid.getInferredType();

                    // optimization: if `self` in method is not assigned, we skip None test
                    if (funcInfo != null)
                    {
                        // check whether the id is the `self`
                        if (funcInfo.isMethod && funcInfo.getParams().indexOf(tid.name) == 0)
                        {
                            isSelfConstant = false;
                        }
                        
                    }

                } else if (target instanceof IndexExpr) {
                    IndexExpr tindex = (IndexExpr) target;
                    analyzeIndexAddr(tindex);
                    commentAssign = "Assign to index expression";
                    targetType = tindex.getInferredType();
                } else if (target instanceof MemberExpr) {
                    MemberExpr tmember = (MemberExpr) target;
                    analyzeMemberExprAddr(tmember);
                    commentAssign = "Assign to member expression";
                    targetType = tmember.getInferredType();
                } else {
                    System.out.println("[AssignStmt] The target is not supported now!");
                    continue;
                }

                if (targetType.equals(assignValueType) || assignValueType.equals(Type.EMPTY_TYPE))
                {
                    backend.emitSW(
                        valReg, tgtAddrReg, 0,
                        commentAssign
                    );
                } else if (targetType.equals(Type.OBJECT_TYPE)) {

                    if (assignValueType.equals(Type.INT_TYPE))
                    {
                        // box int before assigned to object
                        backend.emitMV(A0, valReg, "Move to $a0 for boxing");
                        backend.emitJAL(makeint, "Box integer");
                        backend.emitSW(
                            A0, tgtAddrReg, 0,
                            commentAssign
                        );
                    } else if (assignValueType.equals(Type.BOOL_TYPE)) {
                        // box bool before assigned to object
                        backend.emitMV(A0, valReg, "Move to $a0 for boxing");
                        backend.emitJAL(makebool, "Box bool");
                        backend.emitSW(
                            A0, tgtAddrReg, 0,
                            commentAssign
                        );
                    } else  {
                        // assign directly, str, lists, etc.
                        backend.emitSW(valReg, tgtAddrReg, 0, "Assign to object");
                    }

                } else if (targetType instanceof ClassValueType) {
                    backend.emitSW(valReg, tgtAddrReg, 0, "Assign user defined class object");
                } else {
                    System.out.println("[AssignStmt] Fail to assign " + targetType);
                    continue;
                }
            }

            FreeReg(2);


            return null;
        }

        @Override
        public Void analyze(ListExpr listexpr)
        {
            RiscVBackend.Register retReg = getReturnReg(listexpr);
            int idx = 0;
            SaveBusyReg();

            // push arugments for list construction
            backend.emitADDI(SP, SP,
                -(listexpr.elements.size() + 1) * backend.getWordSize(),
                "Push list elements"
            );

            for (Expr e: listexpr.elements)
            {
                idx++;
                e.dispatch(this);
                backend.emitSW(
                    retReg, SP, idx * backend.getWordSize(),
                    String.format("Push list element %d", idx)
                );
            }
            backend.emitLI(
                retReg, idx, "Assign list length");
            backend.emitSW(
                retReg, SP, 0,
                String.format("Push list length %d", idx)
            );

            // invoke list construction
            backend.emitJAL(conslist, "Move values to new list object");
            backend.emitADDI(SP, SP,
                (listexpr.elements.size() + 1) * backend.getWordSize(),
                "Pop list elements"
            );

            LoadBusyReg();
            backend.emitMV(retReg, A0, "Move list on heap to target reg.");

            return null;
        }


        @Override
        public Void analyze(IfExpr ifExpr){
            Label elsePart = generateLocalLabel();
            Label endOfIfExpr = generateLocalLabel();
            RiscVBackend.Register ret = getReturnReg(ifExpr);
            // if part
            if (ifExpr.condition instanceof BooleanLiteral){
                BooleanLiteral b = (BooleanLiteral) ifExpr.condition;
                if (!b.value){
                    backend.emitJ(elsePart, "Branch on false");
                }
            } else {
                RiscVBackend.Register val = getFreeReg();
                ifExpr.condition.dispatch(this);
                backend.emitMV(A0, val, "Load result of condition");
                backend.emitBEQZ(A0, elsePart, "Branch on false");
                FreeReg(1);
            }
            // then part
            RiscVBackend.Register thenVal = getFreeReg();
            ifExpr.thenExpr.dispatch(this);
            backend.emitMV(ret, thenVal, "Load then part value");
            backend.emitJ(endOfIfExpr, "Go to the end of if expression");
            // else part
            backend.emitLocalLabel(elsePart, "Else part");
            RiscVBackend.Register elseVal = getFreeReg();
            ifExpr.elseExpr.dispatch(this);
            backend.emitMV(ret, elseVal, "Load else part value");
            backend.emitLocalLabel(endOfIfExpr, "End of if-else expression");
            FreeReg(2);
            return null;
        }

        @Override
        public Void analyze(IfStmt ifStmt){
            Label endif = generateLocalLabel();
            Label elseBody = generateLocalLabel();
            if (ifStmt.condition instanceof BooleanLiteral){
                BooleanLiteral b = (BooleanLiteral) ifStmt.condition;
                if (!b.value){
                    backend.emitJ(elseBody, "Branch on false");
                }
            } else {
                RiscVBackend.Register val = getFreeReg();
                ifStmt.condition.dispatch(this);
                backend.emitMV(A0, val, "Load result of condition");
                backend.emitBEQZ(A0, elseBody, "Branch on false");
                FreeReg(1);
            }
            for (Stmt stmt: ifStmt.thenBody){
                stmt.dispatch(this);
            }
            backend.emitJ(endif, "Then body complete; jump to end-if");
            backend.emitLocalLabel(elseBody, "Else body");
            for (Stmt stmt: ifStmt.elseBody){
                stmt.dispatch(this);
            }
            backend.emitLocalLabel(endif, "End of if-else statement");
            return null;
        }

        @Override
        public Void analyze(WhileStmt whileStmt){
            Label topOfLoop = generateLocalLabel();
            Label loopConditon = generateLocalLabel();
            backend.emitJ(loopConditon, "Jump to loop test");
            // loop body
            backend.emitLocalLabel(topOfLoop, "Top of while loop");
            for (Stmt stmt: whileStmt.body){
                stmt.dispatch(this);
            }
            // loop condition
            backend.emitLocalLabel(loopConditon, "Test loop condition");
            RiscVBackend.Register val = getFreeReg();
            whileStmt.condition.dispatch(this);
            backend.emitMV(A0, val, "Load result of condition");
            backend.emitBNEZ(A0, topOfLoop, "Branch on false");
            FreeReg(1);
            return null;
        }

        @Override
        public Void analyze(ForListExpr expr)
        {
            backend.emitComment("ForListExpr");
            ListValueType lsttype = (ListValueType) expr.iterable.getInferredType();

            if (!lsttype.elementType.equals(Type.INT_TYPE))
            {
                System.out.println("[ForListExpr] for-list only support int list now!");
                return null;
            }

            Label notNone = generateLocalLabel();
            // Label forLoopHeader = generateLocalLabel();
            // Label forLoopFooter = generateLocalLabel();
            Label constructSrcLoop = generateLocalLabel();

            RiscVBackend.Register retReg = getReturnReg(expr);
            RiscVBackend.Register lstsrcReg = getFreeReg();
            expr.iterable.dispatch(this);
            RiscVBackend.Register idaddrReg = getFreeReg();
            analyzeIDAddr(expr.identifier);
            RiscVBackend.Register lenReg = getFreeReg();
            RiscVBackend.Register lstsrcptReg = getFreeReg();
            RiscVBackend.Register cntReg = getFreeReg();
            RiscVBackend.Register valReg = getFreeReg();
            

            backend.emitBNEZ(lstsrcReg, notNone, "Ensure not None");
            backend.emitJ(errorNone, "Go to error handler");

            // not None
            backend.emitLocalLabel(notNone, "Not None");

            backend.emitLW(lenReg, lstsrcReg, "@.__len__", "Get attribute __len__");
            backend.emitADDI(lstsrcptReg, lstsrcReg, 12, "Find source list element");
            backend.emitLI(A0, 4, "WordSize");
            backend.emitMUL(A1, lenReg, A0, "Offset for all elements");
            backend.emitADD(lstsrcptReg, lstsrcptReg, A1, "Find last element");

            // initilize source list
            int idx = 0;

            // save reg. before function call
            SaveBusyReg();

            // push arugments for list construction
            backend.emitMV(cntReg, lenReg, "initialize cnter");
            backend.emitLocalLabel(constructSrcLoop, "consturct source list");

            backend.emitADDI(SP, SP,
                -backend.getWordSize(),
                "Push list elements"
            );

            // push zeros inside it
            backend.emitLW(A0, lstsrcptReg, 0, "Load src. list element");
            backend.emitSW(A0, idaddrReg, 0, "Save to id");

            expr.element.dispatch(this);
            backend.emitSW(
                valReg, SP, 0,
                String.format("Push list element %d", idx)
            );

            // update counter and stop until constructs the list
            backend.emitADDI(cntReg, cntReg, -1, "update cnter");
            backend.emitADDI(lstsrcptReg, lstsrcptReg, -4, "update pointer to src. list");

            backend.emitBNEZ(cntReg, constructSrcLoop, "stop initialize");

            backend.emitADDI(SP, SP,
                -backend.getWordSize(),
                "Push list len"
            );
            backend.emitSW(
                lenReg, SP, 0,
                "Push list length"
            );
            // invoke list construction
            backend.emitJAL(conslist, "Move values to new list object");

            backend.emitLW(A1, lstsrcReg, "@.__len__", "Get attribute __len__");
            backend.emitLI(A2, 4, "One word");
            backend.emitMUL(A1, A1, A2, "Compute stack size for elements");
            backend.emitADDI(A1, A1, 4, "One extra word for saving length");

            backend.emitADD(SP, SP, A1, "Pop list elements");

            // load reg. after function call
            LoadBusyReg();
            backend.emitMV(retReg, A0, "Move list on heap to target reg.");

            FreeReg(6);

            return null;
        }

        @Override
        public Void analyze(ForStmt forStmt){
            backend.emitComment("For stmt");
            Label notNone = generateLocalLabel();
            Label forLoopHeader = generateLocalLabel();
            Label forLoopFooter = generateLocalLabel();
            if (forStmt.iterable.getInferredType().isListType()){
                RiscVBackend.Register lst = getFreeReg();
                forStmt.iterable.dispatch(this);

                RiscVBackend.Register idx = getFreeReg();
                backend.emitBNEZ(lst, notNone, "Ensure not None");
                backend.emitJ(errorNone, "Go to error handler");

                // not None
                backend.emitLocalLabel(notNone, "Not None");
                backend.emitMV(idx, ZERO, "Initialize for-loop index");
                // for-loop header
                backend.emitLocalLabel(forLoopHeader, "for-loop header");
                backend.emitLW(A0, lst, "@.__len__", "Get attribute __len__");
                backend.emitBGEU(idx, A0, forLoopFooter, "Exit loop if idx >= len(iter)");
                backend.emitADDI(idx, idx, 1, "Increment idx");
                // Compute list element offset
                backend.emitADDI(A1, idx, 3, "Compute list element offset in words");
                backend.emitLI(A0, backend.getWordSize(), "Word size in bytes");
                backend.emitMUL(A1, A1, A0, "Compute list element offset in bytes");
                backend.emitADD(A1, lst, A1, "Pointer to list element");
                backend.emitLW(A0, A1, 0, "Get list element");

                RiscVBackend.Register targetAddrReg = getFreeReg();
                analyzeIDAddr(forStmt.identifier);
                backend.emitSW(A0, targetAddrReg, 0, String.format("Assign: ", forStmt.identifier.name));
                FreeReg(1);

                SaveBusyReg();
                for (Stmt stmt: forStmt.body){
                    stmt.dispatch(this);
                }
                LoadBusyReg();
                backend.emitJ(forLoopHeader, "Loop back to header");
                backend.emitLocalLabel(forLoopFooter, "for-loop footer");
                FreeReg(2);
            }else if (forStmt.iterable.getInferredType().equals(Type.STR_TYPE)){
                requireInitChar = true;
                RiscVBackend.Register str = getFreeReg();
                forStmt.iterable.dispatch(this);

                RiscVBackend.Register idx = getFreeReg();
                backend.emitMV(idx, ZERO, "Initialize for-loop index");
                // for-loop header
                backend.emitLocalLabel(forLoopHeader, "for-loop header");
                // Use A0 to store the length of the list
                backend.emitLW(A0, str, "@.__len__", "Get attribute __len__");
                backend.emitBGEU(idx, A0, forLoopFooter, "Exit loop if idx >= len(iter)");
                // We don't need length anymore, you can use A0
                backend.emitMV(A1, str, "Load str address");
                RiscVBackend.Register tmp = getFreeReg();
                backend.emitADDI(tmp, idx, 1, "Increment index for next iteration");
                backend.emitADDI(idx, idx, "@.__elts__", "Convert index to offset to char in bytes");
                backend.emitADD(idx, A1, idx, "Get pointer to char");
                backend.emitLBU(idx, idx, 0, "Load character");
                backend.emitLI(A0, 20, "");
                backend.emitMUL(idx, idx, A0, "Multiply by size of string object");
                backend.emitLA(A0, allChars, "Index into single-char table");
                backend.emitADD(A0, A0, idx, "");

                RiscVBackend.Register targetAddrReg = getFreeReg();
                analyzeIDAddr(forStmt.identifier);
                backend.emitSW(A0, targetAddrReg, 0, String.format("Assign: ", forStmt.identifier.name));
                backend.emitMV(idx, tmp, "Move back to idx");
                FreeReg(2);
                SaveBusyReg();
                for (Stmt stmt: forStmt.body){
                    stmt.dispatch(this);
                }
                LoadBusyReg();
                backend.emitJ(forLoopHeader, "Loop back to header");
                backend.emitLocalLabel(forLoopFooter, "for-loop footer");
                FreeReg(2);
            }
            else{
                System.out.println("[ForStmt] Not support " + forStmt.iterable.getInferredType() + " list");
            }
            return null;
        }

        @Override
        public Void analyze(UnaryExpr expr){
            backend.emitComment("UnaryExpr: " + expr.operator);

            RiscVBackend.Register retreg = getReturnReg(expr);
            RiscVBackend.Register ereg = getFreeReg();
            expr.operand.dispatch(this);

            switch (expr.operator) {
                case "-":
                    backend.emitSUB(retreg, ZERO, ereg, "Unary negation");
                    break;
                case "not":
                    backend.emitSEQZ(retreg, ereg, "Logical not");
                    break;
                default:
                    System.out.println("[UnaryExpr] Have not implemented " + expr.operator);
                    backend.emitMV(retreg, ZERO, "Unary op. Error Fallback");
            }
            FreeReg(1);
            return null;
        }

        @Override
        public Void analyze(BinaryExpr expr) {
            Expr el = expr.left;
            Expr er = expr.right;

            backend.emitComment("BinaryExpr: " + expr.operator);

            // one-char equal optiimzation for `stdlib.py` testcase
            if (expr.operator.equals("==") && er instanceof StringLiteral)
            {
                StringLiteral sltr = (StringLiteral) er;
                if(sltr.value.length() == 1)
                {
                    int asciiVal = (int) sltr.value.charAt(0);
                    Label falseLabel = generateLocalLabel();
                    Label endLabel = generateLocalLabel();
                    RiscVBackend.Register retreg = getReturnReg(expr);
                    RiscVBackend.Register elreg = getFreeReg();
                    el.dispatch(this);

                    // lw t0, @.__len__(a1)
                    backend.emitLW(A0, elreg, "@.__len__","get length of LHS string");
                    // bne t0, t1, streql_no
                    backend.emitLI(A1, 1, "judge unit size");
                    backend.emitBNE(A0, A1, falseLabel, "if LHS str not unit length");
                    // lbu t2, @.__str__(a1)
                    backend.emitLBU(A0, elreg, "@.__str__", "Load one char.");
                    // load RHS ascii
                    backend.emitLI(A1, asciiVal, "Load literal ascii");

                    backend.emitBNE(A0, A1, falseLabel, "if value not the same");

                    backend.emitLI(retreg, 1, "Return true");
                    backend.emitJ(endLabel, "jump to the end");


                    backend.emitLocalLabel(falseLabel, "");
                    backend.emitLI(retreg, 0, "Return false");

                    backend.emitLocalLabel(endLabel, "end of streq (unit len)");
                    FreeReg(1);
                    return null;
                }
                
            }

            RiscVBackend.Register retreg = getReturnReg(expr);
            // Don't need to analyze right expression if left is true in `or` or if false in `and`
            switch (expr.operator){
                case "and":
                    Label done_and = generateLocalLabel();
                    RiscVBackend.Register elregAnd = getFreeReg();
                    el.dispatch(this);
                    backend.emitMV(A0, elregAnd, "Load left expression in operation: and");
                    backend.emitBEQZ(A0, done_and, "Operator and: short-circuit left operand");
                    RiscVBackend.Register erregAnd = getFreeReg();
                    er.dispatch(this);
                    backend.emitMV(A0, erregAnd, "Load right expression in operation: and");
                    backend.emitLocalLabel(done_and, "Done evaluating operator: and");
                    backend.emitMV(retreg, A0, "Move the result to return register");
                    FreeReg(2);
                    return null;
                case "or":
                    Label done_or = generateLocalLabel();
                    RiscVBackend.Register elregOr = getFreeReg();
                    el.dispatch(this);
                    backend.emitMV(A0, elregOr, "Load left expression in operation: and");
                    backend.emitBNEZ(A0, done_or, "Operator and: short-circuit left operand");
                    RiscVBackend.Register erregOr = getFreeReg();
                    er.dispatch(this);
                    backend.emitMV(A0, erregOr, "Load right expression in operation: or");
                    backend.emitLocalLabel(done_or, "Done evaluating operator: or");
                    backend.emitMV(retreg, A0, "Move the result to return register");
                    FreeReg(2);
                    return null;
            }
            RiscVBackend.Register elreg = getFreeReg();
            el.dispatch(this);
            RiscVBackend.Register erreg = getFreeReg();
            er.dispatch(this);
            switch (expr.operator) {
                case "+":
                    // concatenation of two lists
                    if (el.getInferredType() instanceof ListValueType)
                    {
                        // concatenate two lists
                        backend.emitADDI(
                            SP, SP, -4 * backend.getWordSize(),
                            "Push args. for concat."
                        );
                        backend.emitSW(elreg, SP, backend.getWordSize(), "Push left list.");
                        backend.emitSW(erreg, SP, 0, "Push right list.");

                        // conversion for left list
                        backend.emitLA(A0, new Label("noconv"), "Identity conversion");
                        backend.emitSW(A0, SP, 3 * backend.getWordSize(), "Push left conversion.");

                        // conversion for right list
                        backend.emitLA(A0, new Label("noconv"), "Identity conversion");
                        backend.emitSW(A0, SP, 2 * backend.getWordSize(), "Push right conversion.");

                        backend.emitJAL(new Label("concat"), "Call runtime concatenation routine.");
                        backend.emitADDI(
                            SP, SP, 4 * backend.getWordSize(),
                            "Pop args. for concat."
                        );

                        backend.emitMV(retreg, A0, "Move concat list to returned reg.");
                    } else if (el.getInferredType().equals(Type.STR_TYPE)) {
                        SaveBusyReg();
                        backend.emitADDI(
                                SP, SP, -2 * backend.getWordSize(), "Push two string to concat.");
                        backend.emitSW(erreg, SP, 0, "Push left string");
                        backend.emitSW(elreg, SP, backend.getWordSize(), "Push right string");
                        backend.emitJAL(strcat, "Invoke strcat");
                        backend.emitADDI(
                                SP, SP, 2 * backend.getWordSize(), "Pop two strings");
                        LoadBusyReg();
                        backend.emitMV(retreg, A0, "Move returned string to target register");
                    } else {
                        // simple addition for two integers
                        backend.emitADD(retreg, elreg, erreg, "ADD");
                    }
                    break;
                case "-":
                    backend.emitSUB(retreg, elreg, erreg, "SUB");
                    break;
                case "*":
                    backend.emitMUL(retreg, elreg, erreg, "MUL");
                    break;
                case "//":
                    Label divCheckSign = generateLocalLabel();
                    Label divDone = generateLocalLabel();
                    Label divAdjustOperand = generateLocalLabel();
                    backend.emitBNEZ(erreg, divCheckSign, "Ensure non-zero divisor");
                    backend.emitJ(errorDiv, "Go to error handler");
                    // if divisor is non-zero
                    backend.emitLocalLabel(divCheckSign, "Divisor is non-zero");
                    RiscVBackend.Register temp = getFreeReg();
                    backend.emitXOR(temp, elreg, erreg, "Check for same sign");
                    backend.emitBLTZ(temp, divAdjustOperand, "if !=, need to adjust left operand");
                    backend.emitDIV(retreg, elreg, erreg, "DIV");
                    backend.emitJ(divDone, "Go to end of Operator //");
                    // Adjust operand
                    backend.emitLocalLabel(divAdjustOperand, "Operands have differing signs");
                    backend.emitSLT(temp, ZERO, erreg, "tmp = 1 if right > 0 else 0");
                    backend.emitADD(temp, temp, temp, "tmp *= 2");
                    backend.emitADDI(temp, temp, -1, "tmp = 1 if right >= 0 else -1");
                    backend.emitADD(temp, elreg, temp, "Adjust left operand");
                    backend.emitDIV(temp, temp, erreg, "Adjusted division, toward 0");
                    backend.emitADDI(retreg, temp, -1, "Complete division when sign !=");
                    backend.emitLocalLabel(divDone, "End of //");
                    FreeReg(1);
                    break;
                case "%":
                    Label modNonZero = generateLocalLabel();
                    Label modDone = generateLocalLabel();
                    backend.emitBNEZ(erreg, modNonZero, "Ensure non-zero divisor");
                    backend.emitJ(errorDiv, "Go to error handler");
                    // Divisor is non-zero
                    backend.emitLocalLabel(modNonZero, "Divisor is non-zero");
                    backend.emitREM(retreg, elreg, erreg, "MOD");
                    backend.emitBEQZ(retreg, modDone, "If no remainder, no adjustment");
                    RiscVBackend.Register tmp = getFreeReg();
                    backend.emitXOR(tmp, retreg, erreg, "Check for differing signs");
                    backend.emitBGEZ(tmp, modDone, "Don't adjust if signs equal");
                    backend.emitADD(retreg, retreg, erreg, "Adjust");
                    // Store result
                    backend.emitLocalLabel(modDone, "End of %");
                    FreeReg(1);
                    break;
                case "==":
                    if (el.getInferredType().equals(Type.STR_TYPE))
                    {
                        backend.emitADDI(SP, SP, -2 * backend.getWordSize(), "Push arguments streql");
                        backend.emitSW(elreg, SP, 0, "Push LHS string");
                        backend.emitSW(erreg, SP, backend.getWordSize(), "Push RHS string");
                        backend.emitJAL(streql, "Call string == function");
                        backend.emitMV(retreg, A0, "Move returned value back");
                        backend.emitADDI(SP, SP, 2 * backend.getWordSize(), "Pop arguments streql");


                    } else {
                        backend.emitXOR(erreg, elreg, erreg, "Operator ==");
                        backend.emitSEQZ(retreg, erreg,"Operator == (..contd)");
                    }

                    break;
                case "!=":
                    if (el.getInferredType().equals(Type.STR_TYPE))
                    {
                        backend.emitADDI(SP, SP, -2 * backend.getWordSize(), "Push arguments strneql");
                        backend.emitSW(elreg, SP, 0, "Push LHS string");
                        backend.emitSW(erreg, SP, backend.getWordSize(), "Push RHS string");
                        backend.emitJAL(strneql, "Call string != function");
                        backend.emitMV(retreg, A0, "Move returned value back");
                        backend.emitADDI(SP, SP, 2* backend.getWordSize(), "Pop arguments strneql");
                    } else {
                        backend.emitXOR(erreg, elreg, erreg, "Operator !=");
                        backend.emitSNEZ(retreg, erreg, "Operator != (..contd)");
                    }

                    break;
                case "<":
                    backend.emitSLT(retreg, elreg, erreg, "Operator <");
                    break;
                case "<=":
                    backend.emitSLT(erreg, erreg, elreg, "Operator <=");
                    backend.emitSEQZ(retreg, erreg, "Operator <= (..contd)");
                    break;
                case ">":
                    backend.emitSLT(retreg, erreg, elreg, "Operator >");
                    break;
                case ">=":
                    backend.emitSLT(erreg, elreg, erreg, "Operator >=");
                    backend.emitSEQZ(retreg, erreg, "Operator >= (..contd)");
                    break;
                case "is":
                      // xor a0, t0, a0                           # Compare references
                      // seqz a0, a0                              # Operator is
                    backend.emitXOR(retreg, elreg, erreg, "Compare references");
                    backend.emitSEQZ(retreg, retreg, "Operator is");
                    break;
                default:
                    System.out.println("[BinaryExpr] Have not implemented " + expr.operator);
                    backend.emitMV(retreg, ZERO, "Binary op. Error Fallback");
            }

            FreeReg(2);
            return null;
        }

        @Override
        public Void analyze(IntegerLiteral intlitrl) {
            backend.emitLI(getReturnReg(intlitrl), intlitrl.value, "Load integer literal: " + String.valueOf(intlitrl.value));
            return null;
        }

        @Override
        public Void analyze(BooleanLiteral boollitrl){
            backend.emitLI(getReturnReg(boollitrl), boollitrl.value ? 1 : 0, "Load boolean literal: " + String.valueOf(boollitrl.value));
            return null;
        }

        @Override
        public Void analyze(StringLiteral stringLiteral){
            backend.emitLA(getReturnReg(stringLiteral), constants.getStrConstant(stringLiteral.value), "Load string literal");
            return null;
        }

        public Void analyzeIDAddr(Identifier id)
        {
            RiscVBackend.Register retReg = getReturnReg(id);
            analyzeIDAddr(id, retReg);

            return null;
        }

        public Void analyzeIDAddr(Identifier id, RiscVBackend.Register retReg)
        {
            String name = id.name;

            if (funcInfo == null) {
                backend.emitLA(retReg, new Label("$" + name), "Load addr: " + name);
            } else {
                if (sym.isGlobal(name)) {
                    backend.emitLA(retReg, new Label("$" + name), "Load addr: " + name);
                } else {
                    StackVarInfo varInfo = (StackVarInfo) sym.get(name);
                    FuncInfo varfuncInfo = varInfo.getFuncInfo();
                    int depthDiff = funcInfo.getDepth() - varfuncInfo.getDepth();
                    // variable is in current scope
                    if (depthDiff == 0) {
                        backend.emitADDI(retReg, FP, -(funcInfo.getVarIndex(name) + 1) * backend.getWordSize(), "Load local var addr: " + name);
                    } else {
                        int varFrameOnStackOffset = funcInfo.getParams().size() + depthDiff - 1;
                        varFrameOnStackOffset *= backend.getWordSize();

                        String comment = String.format(
                            "Load static link from %s to %s",
                            varfuncInfo.getFuncName(),
                            funcInfo.getFuncName()
                        );
                        backend.emitLW(retReg, FP, varFrameOnStackOffset, comment);
                        backend.emitADDI(retReg, retReg, -(varfuncInfo.getVarIndex(name) + 1) * backend.getWordSize(), "Load nonlocal var addr: " + name);
                    }
                }
            }

            return null;
        }

        @Override
        public Void analyze(Identifier id) {
            RiscVBackend.Register retReg = getReturnReg(id);
            String name = id.name;

            // top-level function could only use global variables
            if (funcInfo == null) {
                 // optimize when it is a constant int
                if (globalSymbols.isConst(name) && id.getInferredType().equals(Type.INT_TYPE))
                {
                    VarInfo varInfo = (VarInfo) sym.get(name);
                    IntegerLiteral intlitrl = (IntegerLiteral) varInfo.getInitialValue();
                    backend.emitLI(retReg, intlitrl.value, "Load const. global: " + name);
                } else {
                    backend.emitLW(retReg, new Label("$" + name), "Load global: " + name);
                }
            } else {
                // the symbol has a global declaration
                if (sym.get(name) instanceof GlobalVarInfo) {
                    backend.emitLW(retReg, new Label("$" + name), "Load global: " + name);
                } else {
                    StackVarInfo varInfo = (StackVarInfo) sym.get(name);
                    FuncInfo varfuncInfo = varInfo.getFuncInfo();
                    int depthDiff = funcInfo.getDepth() - varfuncInfo.getDepth();
                    // variable is in current scope
                    if (depthDiff == 0) {
                        backend.emitLW(retReg, FP, -(funcInfo.getVarIndex(name) + 1) * backend.getWordSize(), "Load local var: " + name);
                    } else {

                        int varFrameOnStackOffset = funcInfo.getParams().size() + depthDiff - 1;
                        varFrameOnStackOffset *= backend.getWordSize();

                        String comment = String.format(
                            "Load static link from %s to %s",
                            varfuncInfo.getFuncName(),
                            funcInfo.getFuncName()
                        );
                        backend.emitLW(A0, FP, varFrameOnStackOffset, comment);
                        backend.emitLW(retReg, A0, -(varfuncInfo.getVarIndex(name) + 1) * backend.getWordSize(), "Load non-local var: " + name);
                    }

                }
            }
            return null;
        }

        // TODO: Example analysis that (incorrectly) emits a no-op.
        @Override
        public Void analyze(ReturnStmt stmt) {
            RiscVBackend.Register valReg = getFreeReg();
            if (stmt.value == null) 
            {
                FreeReg(1);
                ForceFreeAllReg();
                backend.emitJ(epilogue, "Jump to function epilogue");
                return null;
            }

            // optimization: tail call optimization
            if (stmt.value instanceof CallExpr)
            {
                CallExpr callexpr = (CallExpr) stmt.value;
                String funcname = callexpr.function.name;

                // is tail call
                if (funcname.equals(funcInfo.getBaseName()))
                {
                    // set the arguments to the params
                    for (Expr arg: callexpr.args){
                        arg.dispatch(this);
                        int idx =  callexpr.args.indexOf(arg);

                        backend.emitSW(valReg, FP, idx * backend.getWordSize(), String.format("Tail call set argument %s", idx));
                    }

                    // Reset local var.
                    for (StackVarInfo svi : funcInfo.getLocals()) {
                        if (svi.getVarType().equals(Type.INT_TYPE)) {
                            int imm = ((IntegerLiteral) svi.getInitialValue()).value;
                            backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), imm));
                        } else if (svi.getVarType().equals(Type.BOOL_TYPE)){
                            boolean value = ((BooleanLiteral) svi.getInitialValue()).value;
                            int imm = value ? 1 : 0;
                            backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), value));
                        } else if (svi.getVarType().equals(Type.STR_TYPE)){
                            String str = ((StringLiteral) svi.getInitialValue()).value;
                            backend.emitLA(A0, constants.getStrConstant(str), "Load string literal");
                        } else {
                            backend.emitMV(A0, ZERO, String.format("Load None"));
                        }
                        backend.emitSW(A0, FP, -(funcInfo.getVarIndex(svi.getVarName()) + 1) * backend.getWordSize(), "local variable");
                    }

                    FreeReg(1);
                    ForceFreeAllReg();

                    backend.emitJ(funcInfo.getCodeStartLabel(), "Tail cail invoke self");
                    return null;
                }
            }

            // tail call optimization for method call
            if (stmt.value instanceof MethodCallExpr)
            {
                MethodCallExpr callexpr = (MethodCallExpr) stmt.value;
                String funcname = callexpr.method.member.name;
                ClassValueType objType = (ClassValueType) callexpr.method.object.getInferredType();
                ClassInfo objClassInfo = (ClassInfo) sym.get(objType.className());
                int methodIndex = objClassInfo.getMethodIndex(funcname);
                FuncInfo callfuncInfo = (FuncInfo) objClassInfo.methods.get(methodIndex);

                // is tail recursive call
                if (callfuncInfo.getFuncName().equals(funcInfo.getFuncName()))
                {
                    // set obj
                    callexpr.method.object.dispatch(this);
                    backend.emitSW(valReg, FP, 0, "Tail call set argument self");

                    // set the arguments to the params
                    for (Expr arg: callexpr.args){
                        arg.dispatch(this);
                        int idx =  callexpr.args.indexOf(arg);

                        backend.emitSW(valReg, FP, (idx + 1) * backend.getWordSize(), String.format("Tail call set argument %s", idx));
                    }

                    // Reset local var.
                    for (StackVarInfo svi : funcInfo.getLocals()) {
                        if (svi.getVarType().equals(Type.INT_TYPE)) {
                            int imm = ((IntegerLiteral) svi.getInitialValue()).value;
                            backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), imm));
                        } else if (svi.getVarType().equals(Type.BOOL_TYPE)){
                            boolean value = ((BooleanLiteral) svi.getInitialValue()).value;
                            int imm = value ? 1 : 0;
                            backend.emitLI(A0, imm, String.format("Load %s literal: %s ", svi.getVarName(), value));
                        } else if (svi.getVarType().equals(Type.STR_TYPE)){
                            String str = ((StringLiteral) svi.getInitialValue()).value;
                            backend.emitLA(A0, constants.getStrConstant(str), "Load string literal");
                        } else {
                            backend.emitMV(A0, ZERO, String.format("Load None"));
                        }
                        backend.emitSW(A0, FP, -(funcInfo.getVarIndex(svi.getVarName()) + 1) * backend.getWordSize(), "local variable");
                    }

                    FreeReg(1);
                    ForceFreeAllReg();

                    backend.emitJ(funcInfo.getCodeStartLabel(), "Tail cail invoke self");
                    return null;
                }
            }


            stmt.value.dispatch(this);
            backend.emitMV(A0, valReg, "Load return value");
            FreeReg(1);
            ForceFreeAllReg();
            backend.emitJ(epilogue, "Jump to function epilogue");
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
        backend.defineSym("requireInitChar", requireInitChar ? 1 : 0);
        backend.defineSym("maxCharAscii", constants.maxCharAscii);

        emitStdFunc("makeint");
        emitStdFunc("makebool");
        emitStdFunc("conslist");
        emitStdFunc("concat");
        emitStdFunc("noconv");
        emitStdFunc("initchars");
        emitStdFunc("strcat");
        emitStdFunc("streql");
        emitStdFunc("strneql");

        emitErrorFunc(errorArg, "Invalid argument", ERROR_ARG);
        emitErrorFunc(errorNone, "Operation on None", ERROR_NONE);
        emitErrorFunc(errorDiv, "Division by zero", ERROR_DIV_ZERO);
        emitErrorFunc(errorOob, "Index out of bounds", ERROR_OOB);
    }

    /** Emits an error routine labeled ERRLABEL that aborts with message MSG. */
    private void emitErrorFunc(Label errLabel, String msg, int errorCode) {
        backend.emitGlobalLabel(errLabel);
        backend.emitLI(A0, errorCode, "Exit code for: " + msg);
        backend.emitLA(A1, constants.getStrConstant(msg), "Load error message as str");
        backend.emitADDI(
                A1, A1, getAttrOffset(strClass, "__str__"), "Load address of attribute __str__");
        backend.emitJ(abortLabel, "Abort");
    }
}
