package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.*;
import chocopy.common.astnodes.*;

import java.util.List;

import static chocopy.common.analysis.types.Type.*;

/**
 * Analyzer that performs ChocoPy type checks on all nodes. Applied after collecting declarations.
 */
public class TypeChecker extends AbstractNodeAnalyzer<Type> {
    /** The current symbol table (changes depending on the function being analyzed). */
    private SymbolTable<Type> sym;
    /** Collector for errors. */
    private final Errors errors;
    /** Whether enable debug mode for TypeChecker **/
    private static Boolean DEBUG = true;

    private SymbolTable<Type> classTable;
    /**
     * Creates a type checker using GLOBALSYMBOLS for the initial global symbol table and ERRORS0 to
     * receive semantic errors.
     */
    public TypeChecker(SymbolTable<Type> globalSymbols, SymbolTable<Type> classTable0, Errors errors0) {
        sym = globalSymbols;
        errors = errors0;
        classTable = classTable0;
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
            if (stmt instanceof ReturnStmt){
                err(stmt, "Return statement cannot appear at the top level");
            }
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
    public Type analyze(BooleanLiteral b) { return b.setInferredType(BOOL_TYPE); }
    @Override
    public Type analyze(NoneLiteral n){
        return n.setInferredType(ValueType.NONE_TYPE);
    }
    @Override
    public Type analyze(StringLiteral s) { return s.setInferredType(Type.STR_TYPE); }
    @Override
    public Type analyze(BinaryExpr e) {
        Type t1 = e.left.dispatch(this);
        Type t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "+":
                if ((t1 instanceof ListValueType) && (t2 instanceof ListValueType))
                {
                    ListValueType t1l = (ListValueType) t1;
                    ListValueType t2l = (ListValueType) t2;
                    if(t1l.elementType.equals(t2l.elementType))
                    {
                        return e.setInferredType(t1);
                    } else {
                        Type t = Type.getLowestCommonAncestor(
                            classTable.get(t1l.elementType.className()), 
                            classTable.get(t2l.elementType.className())
                        );
                        return e.setInferredType(new ListValueType(classTable.get(t.className())));
                    }
                }

                if (!(INT_TYPE.equals(t1) && INT_TYPE.equals(t2))
                  &&!(STR_TYPE.equals(t1) && STR_TYPE.equals(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                    return INT_TYPE.equals(t1) || INT_TYPE.equals(t2) ? INT_TYPE : OBJECT_TYPE;
                }
                return e.setInferredType(t1);
            case "-":
            case "*":
            case "//":
            case "%":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    t1 = t1 != null? t1 : OBJECT_TYPE;
                    t2 = t2 != null? t2 : OBJECT_TYPE;
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(INT_TYPE);
            case ">":
            case "<":
            case "<=":
            case ">=":
                if (!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "==":
            case "!=":
                if ((!INT_TYPE.equals(t1) || !INT_TYPE.equals(t2))
                        && (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2))
                        && (!STR_TYPE.equals(t1) || !STR_TYPE.equals(t2))) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "or":
            case "and":
                if (!BOOL_TYPE.equals(t1) || !BOOL_TYPE.equals(t2)) {
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "is":
                // t1 = t2 = None or t1 and t2 are the same object in memory
                if (t1.isSpecialType() || t2.isSpecialType()){
                    err(e, "Cannot apply operator `%s` on types `%s` and `%s`", e.operator, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public Type analyze(Identifier id) {
        Type idType = OBJECT_TYPE;
        if (sym.declare(id.name) && !classTable.declare(id.name)) {
            idType = sym.get(id.name);
        } else {
            err(id, "Not a variable: %s", id.name);
        }
        return id.setInferredType(idType);
    }

    @Override
    public Type analyze(VarDef varDef) {
        Type vt = varDef.value.dispatch(this);
        if (!vt.canBeAssignedTo(ValueType.annotationToValueType(varDef.var.type))){
            err(varDef, "Expected type `%s`; got type `%s`", ValueType.annotationToValueType(varDef.var.type), vt);
        }
        return null;
    }

    @Override
    public Type analyze(ConstVarDef varDef) {
        Type vt = varDef.value.dispatch(this);
        if (vt == null){
            return null;
        }
        if (!NONE_TYPE.equals(vt) && !vt.isSubClassOf(ValueType.annotationToValueType(varDef.var.type))){
            err(varDef, "Expected type `%s`; got type `%s`", ValueType.annotationToValueType(varDef.var.type), vt);
        }
        return null;
    }

    @Override
    public Type analyze(ClassDef classDef) {
        for (Declaration decl: classDef.declarations) {
            decl.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(AssignStmt assignStmt){
        Type t = assignStmt.value.dispatch(this);
        boolean typeError = false;
        for(Expr target: assignStmt.targets){
           if (target instanceof Identifier) {
                Identifier id = (Identifier) target;
                if (!typeError && !sym.declareOwn(id.name)) {
                    err(assignStmt, "Cannot assign to variable that is not explicitly declared in this scope: %s", id.name);
                }

                Type tleft = sym.get(id.name);
                if (!typeError && tleft != null && sym.isConst(id.name))
                {
                    err(assignStmt, "Cannot assign to a constant varaible: %s", id.name);
                }
            }

           Type tt = target.dispatch(this);

            // could not assign to str char (e.g: when a = "hello", a[0] = "t" is invalid)
            if (target instanceof IndexExpr)
            {
                IndexExpr indexExpr = (IndexExpr) target; 
                Type tlst = indexExpr.list.getInferredType();
                if (!(tlst instanceof ListValueType))
                {
                    err(indexExpr, "`%s` is not a list type", tlst);
                }
            }

            if (!typeError && t != null && tt != null && !t.canBeAssignedTo(tt)){
                err(assignStmt, "Expected type `%s`; got type `%s`", tt.toString(), t.toString());
                typeError = true;
            }
        }
        if (!typeError && assignStmt.targets.size() > 1 && assignStmt.value.getInferredType().isListType() && assignStmt.value.getInferredType().elementType().equals(NONE_TYPE)){
            err(assignStmt, "Right-hand side of multiple assignment may not be [<None>]");
        }
        return null;
    }

    @Override
    public Type analyze(MemberExpr memberExpr) {
        Type classType = memberExpr.object.dispatch(this);
        Type returnType = OBJECT_TYPE;
        String memberName = memberExpr.member.name;
        if ((classType instanceof ClassValueType) && ((ClassValueType) classType).memberTable.declare(memberName)) {
            returnType = ((ClassValueType) classType).memberTable.get(memberName);
        } else {
            err(memberExpr, "There is no attribute named `%s` in class `%s`", memberName, classType.className());
        }
        return memberExpr.setInferredType(returnType);
    }

    @Override
    public Type analyze(ListExpr listExpr){
        // '[]' => <EMPTY>
        if (listExpr.elements.size() == 0)
        {
            return listExpr.setInferredType(ValueType.EMPTY_TYPE);
        }

        // Judge list type based on element type
        // '[1, 2, 3]'      => ListType(<INT>)
        // '[False, True]'  => ListType(<BOOL>)
        // '[1, False]'     => ListType(<OBJECT>)
        Type listElemType = null;
        for (Expr e : listExpr.elements) {
            e.dispatch(this);

            Type elemType = e.getInferredType();

            if (listElemType == null)
            {
                // First element sets type directly
                listElemType = elemType;
            } else {
                // Hybrid elements constructs a <object> list
                listElemType = Type.getLowestCommonAncestor(listElemType, elemType);
            }
            // Otherwise, keeps the same

        }

        return listExpr.setInferredType(new ListValueType(listElemType));
    }

    @Override
    public Type analyze(IndexExpr indexExpr){
        Type tlst = indexExpr.list.dispatch(this);
        Type tidx = indexExpr.index.dispatch(this);

        if (!tidx.equals(INT_TYPE))
        {
            err(indexExpr, "Index is of non-integer type `%s`", tidx);
        }

        // if tlst is a list, set the element's type into inferred type
        if (tlst instanceof ListValueType)
        {
            ListValueType t = (ListValueType) tlst;

            if (t.elementType.className() != null)
            {
                return indexExpr.setInferredType(classTable.get(t.elementType.className()));
            }
            return indexExpr.setInferredType(t.elementType);
        }
        // if it is string, set the class type directly as returned type
        else if (tlst.equals(STR_TYPE)) 
        {
            indexExpr.list.setInferredType(tlst);
            return indexExpr.setInferredType(tlst);
        } else {
            err(indexExpr, "Cannot index into type `%s`", tlst);
        }

        return tlst;
    }

    @Override
    public Type analyze(UnaryExpr unaryExpr){
        Type t = unaryExpr.operand.dispatch(this);
        if ((!BOOL_TYPE.equals(unaryExpr.operand.kind) || !INT_TYPE.equals(unaryExpr.operand.kind))
         && !(unaryExpr.operator.equals("-") && t.equals(INT_TYPE))
         && !(unaryExpr.operator.equals("not") && t.equals(BOOL_TYPE))){
            err(unaryExpr, "Cannot apply operator `%s` on type `%s`", unaryExpr.operator, t);
        }
        return unaryExpr.setInferredType(t);
    }
    @Override
    public Type analyze(FuncDef funcDef){
        SymbolTable<Type> tmpSym = sym;
        sym = funcDef.sym;
        for (Declaration decl : funcDef.declarations) {
            decl.dispatch(this);
        }
        // p1, p2 := whether the path has a return statement
        // p := whether the current path has a return statement
        // Pass all paths first
        boolean p1=false, p2=false, p=false;
        for (Stmt stmt : funcDef.statements) {
            if (stmt instanceof IfStmt){
                p1 = parseAllPath(funcDef, ((IfStmt) stmt).thenBody);
                p2 = parseAllPath(funcDef, ((IfStmt) stmt).elseBody);
            }
            if (stmt instanceof ReturnStmt){
                Type t = stmt.dispatch(this);
                p = true;
                if (t == null && !NONE_TYPE.canBeAssignedTo(ValueType.annotationToValueType(funcDef.returnType))){
                    err(stmt, "Expected type `%s`; got `None`", ValueType.annotationToValueType(funcDef.returnType));
                }
                else if (t != null && !t.canBeAssignedTo(ValueType.annotationToValueType(funcDef.returnType))){
                    err(stmt, "Expected type `%s`; got type `%s`", ValueType.annotationToValueType(funcDef.returnType), t.toString());
                }
            }
        }
        // If either path does not have a return statement and the current path does not have a return type
        // and None Type cannot be assigned to the declared return type
        if (((!p1 || !p2) && !p)
                && !NONE_TYPE.canBeAssignedTo(ValueType.annotationToValueType(funcDef.returnType)) ){
            err(funcDef.name, "All paths in this function/method must have a return statement: %s", funcDef.name.name);
            return null;
        }
        for (Stmt stmt: funcDef.statements){
            stmt.dispatch(this);
        }
        sym = tmpSym;
        return null;
    }
//    parse all paths with dfs
//    return a boolean shows whether this path has a return statement
    private boolean parseAllPath(FuncDef funcDef, List<Stmt> statements){
        // p1, p2 := whether the path has a return statement
        // p := whether the current path has a return statement
        // Pass all paths first
        boolean p1=false, p2=false, p = false;
        for (Stmt stmt : statements) {
            if (stmt instanceof IfStmt){
                p1 = parseAllPath(funcDef, ((IfStmt) stmt).thenBody);
                p2 = parseAllPath(funcDef, ((IfStmt) stmt).elseBody);
            }
            if (stmt instanceof ReturnStmt){
                stmt.dispatch(this);
                Type t = ((ReturnStmt) stmt).value != null ? ((ReturnStmt) stmt).value.getInferredType(): null;
                if (t == null && !NONE_TYPE.canBeAssignedTo(ValueType.annotationToValueType(funcDef.returnType))){
                    err(stmt, "Expected type `%s`; got `None`", ValueType.annotationToValueType(funcDef.returnType));
                }
                else if (t != null && !t.canBeAssignedTo(ValueType.annotationToValueType(funcDef.returnType))){
                    err(stmt, "Expected type `%s`; got type `%s`", ValueType.annotationToValueType(funcDef.returnType), t.toString());
                }
                p = true;
            }
        }
        // If both paths p1 and p2 have a return statement
        // Or the current path does not have a return statement
        return (p1 && p2) || p;
    }
    @Override
    public Type analyze(IfExpr ifExpr) {
        Type t = ifExpr.condition.dispatch(this);
        if (!t.equals(BOOL_TYPE)) {
            err(ifExpr, "Condition expression cannot be of type `%s`", t.toString());
        }
        Type tThen = ifExpr.thenExpr.dispatch(this);
        Type tElse = ifExpr.elseExpr.dispatch(this);
        Type exprType = Type.getLowestCommonAncestor(tThen, tElse);
        return ifExpr.setInferredType(exprType);
    }

    @Override
    public Type analyze(IfStmt ifStmt) {
        Type t = ifStmt.condition.dispatch(this);

        if (!t.equals(BOOL_TYPE)) {
            err(ifStmt, "Condition expression cannot be of type `%s`", t.toString());
        }
        for (Stmt s : ifStmt.thenBody) {
            s.dispatch(this);
        }
        for (Stmt s : ifStmt.elseBody) {
            s.dispatch(this);
        }
        return null;
    }


    @Override
    public Type analyze(WhileStmt stmt) {
        Type t = stmt.condition.dispatch(this);
        if (!t.equals(BOOL_TYPE)) {
            err(stmt, "Condition expression cannot be of type `%s`", t.toString());
        }
        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }
        return null;
    }

    @Override
    public Type analyze(ForStmt stmt) {
        Type typeIter = stmt.iterable.dispatch(this);
        if (!typeIter.equals(STR_TYPE) && !(typeIter instanceof ListValueType)) {
            err(stmt, "Cannot iterate over value of type `%s`", typeIter.toString());
        } else {
            Type typeId = stmt.identifier.dispatch(this);
            Type iterElementType;
            if (typeIter.equals(STR_TYPE)) {
                iterElementType = typeIter;
            } else {
                iterElementType = typeIter.elementType();
            }
            if (!iterElementType.isSubClassOf(typeId)) {
                err(stmt, "Expected type `%s`; got type `%s`", typeId.className(), iterElementType.className());
            }
        }
        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }

        return null;
    }

    @Override
    public Type analyze(ReturnStmt returnStmt){
        if (returnStmt.value == null){
            return null;
        }
        return returnStmt.value.dispatch(this);
    }
    @Override
    public Type analyze(CallExpr callExpr){
        if (!sym.declare(callExpr.function.name)){
            err(callExpr, "Not a function or class: %s", callExpr.function.name);
        }
        else{
            if (sym.get(callExpr.function.name) instanceof ClassValueType) {
                return callExpr.setInferredType(sym.get(callExpr.function.name));
            } else {
                callExpr.function.dispatch(this);
                FuncType s = (FuncType)sym.get(callExpr.function.name);
                //  I don't know why I have ensured sym.declares but sym.get still could be null;
                if (s == null){
                    return callExpr.setInferredType(NONE_TYPE);
                }
                if (s.parameters.size() != callExpr.args.size()){
                    err(callExpr, "Expected %d arguments; got %d", s.parameters.size(), callExpr.args.size());
                }else{
                    for (int i = 0; i < s.parameters.size(); i++){
                        Type cet = callExpr.args.get(i).dispatch(this);  // cet := Call Expression Type
                        Type fdt = s.parameters.get(i);                          // fdt := Function define Type
                        if (!cet.canBeAssignedTo(fdt)) {
                            err(callExpr, "Expected type `%s`; got type `%s` in parameter %d", fdt.toString(), cet.toString(), i);
                            break;
                        }
                    }
                }
                if (s.returnType == null) {
                    return callExpr.setInferredType(NONE_TYPE);
                }
                return callExpr.setInferredType(s.returnType);
            }
        }
        return null;
    }
    @Override
    public Type analyze(MethodCallExpr methodCallExpr){
        Type classType = methodCallExpr.method.object.dispatch(this);
        Type returnType = OBJECT_TYPE;
        String methodName = methodCallExpr.method.member.name;
        if ((classType instanceof ClassValueType) && ((ClassValueType) classType).memberTable.declare(methodName)){
            returnType = methodCallExpr.method.setInferredType(((ClassValueType) classType).memberTable.get(methodName));
        }else {
            err(methodCallExpr, "There is no method named `%s` in class `%s`", methodName, classType.className());
        }
        if (returnType.equals(OBJECT_TYPE)){
            return returnType;
        }
        // class methods always have the self as one of its arguments
        if (returnType.isFuncType() && methodCallExpr.args.size() != ((FuncType) returnType).parameters.size() - 1){
            err(methodCallExpr, "Expected %d arguments; got %d", ((FuncType) returnType).parameters.size() - 1, methodCallExpr.args.size());
        }else{
            for (int i = 0; i < methodCallExpr.args.size(); i++){
                Type cet = methodCallExpr.args.get(i).dispatch(this);   // cet := Call Expression Type
                // The first param of a method is self. Comparing starts from parms[1]
                Type fdt = ((FuncType) returnType).parameters.get(i+1);         // fdt := Function Define Type
                if (!cet.canBeAssignedTo(fdt)){
                    err(methodCallExpr, "Expected type `%s`; got type `%s` in parameter %d", fdt.toString(), cet.toString(), i+1);
                    break;
                }
            }
        }
        return methodCallExpr.setInferredType(((FuncType) returnType).returnType);
    }

    @Override
    public Type analyze(ForListExpr forlistexpr) {
        Type titer = forlistexpr.iterable.dispatch(this);
        if (!(titer instanceof ListValueType))
        {
            err(forlistexpr.iterable, "ForListExpr only support list as iterable. But found: %s", titer);
            return null;
        }

        if (classTable.declare(forlistexpr.identifier.name)) {
            err(forlistexpr, "Cannot shadow class name: %s", forlistexpr.identifier.name);
            return Type.EMPTY_TYPE;
        }

        // the control variable's type depends on iterable returned list
        ValueType tcontrol = ((ListValueType) titer).elementType;

        SymbolTable<Type> tmpSym = sym; // save scope
        sym = new SymbolTable<>(sym); // create a new scope

        forlistexpr.identifier.setInferredType(tcontrol);
        sym.put(forlistexpr.identifier.name, tcontrol);
        
        Type telem = forlistexpr.element.dispatch(this);

        sym = tmpSym; // switch back to the outer scope
        return forlistexpr.setInferredType(new ListValueType(telem));
    }
}
