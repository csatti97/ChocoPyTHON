package chocopy.pa2;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.astnodes.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<Type> {
    /**
     * Current symbol table. Changes with new declarative region.
     */
    private SymbolTable<Type> sym = new SymbolTable<>();
    /**
     * Global symbol table.
     */
    private final SymbolTable<Type> globals = sym;

    /**
     * Receiver for semantic error messages.
     */
    private final Errors errors;

    // record defined class
    public SymbolTable<Type> classTable = new SymbolTable<>();

    /**
     * A new declaration analyzer sending errors to ERRORS0.
     */
    public DeclarationAnalyzer(Errors errors0) {
        errors = errors0;
        // These symbols avoid declarations of variables of the same name
        // as the built-in types.
        sym.put("object", Type.OBJECT_TYPE);
        sym.put("int", Type.INT_TYPE);
        sym.put("str", Type.STR_TYPE);
        sym.put("bool", Type.BOOL_TYPE);
        classTable.put("object", Type.OBJECT_TYPE);
        classTable.put("int", Type.INT_TYPE);
        classTable.put("str", Type.STR_TYPE);
        classTable.put("bool", Type.BOOL_TYPE);
        classTable.put("<None>", Type.NONE_TYPE);
        classTable.put("<Empty>", Type.EMPTY_TYPE);
        // Add built-in functions
        List<ValueType> valueTypeList = new ArrayList<>();
        valueTypeList.add(ValueType.OBJECT_TYPE);
        sym.put("print", new FuncType(valueTypeList, ValueType.NONE_TYPE));
        sym.put("len", new FuncType(valueTypeList, ValueType.INT_TYPE));
        sym.put("input", new FuncType(new ArrayList<>(), ValueType.STR_TYPE));

    }

    public SymbolTable<Type> getGlobals() {
        return globals;
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
        // Sub-pass 1: Scan all the definition and detect invalid/duplicate def. of var. && class && func.
        for (Declaration decl : program.declarations) {
            if (sym.declareOwn(decl.getIdentifier().name)) {
                err(decl.getIdentifier(), "Duplicate declaration of identifier in same scope: %s", decl.getIdentifier().name);
            }
            // All symbols are set to OBJECT_TYPE temporarilty to detect duplication.
            // Their type would be decided on Sub-pass 2
            if (decl instanceof VarDef) {
                sym.put(decl.getIdentifier().name, Type.OBJECT_TYPE); 
            }
            if (decl instanceof ConstVarDef)
            {
                sym.put(decl.getIdentifier().name, Type.OBJECT_TYPE);
                sym.setConst(decl.getIdentifier().name);
            }
            if (decl instanceof FuncDef) {
                sym.put(decl.getIdentifier().name, new FuncType(Type.OBJECT_TYPE));
            }

            // Remark: Super-class errors depends on the order of class declaration.
            //         That is why we set up an extra sub-pass to detect.
            //         An invalid class would not be recorded and influence further error detection.
            if (decl instanceof ClassDef) {
                if (!sym.declare(((ClassDef) decl).superClass.name)) {
                    err(((ClassDef) decl).superClass, "Super-class not defined: %s", ((ClassDef) decl).superClass.name);
                }else if (!(classTable.declare(((ClassDef) decl).superClass.name))) {
                    err(((ClassDef) decl).superClass, "Super-class must be a class: %s", ((ClassDef) decl).superClass.name);
                } else if (classTable.get(((ClassDef) decl).superClass.name).isSpecialType()) {
                    err(((ClassDef) decl).superClass, "Cannot extend special class: %s", ((ClassDef) decl).superClass.name);
                }
                ClassValueType classType = new ClassValueType(((ClassDef) decl).name.name);
                classTable.put(((ClassDef) decl).name.name, classType);
                sym.put(((ClassDef) decl).name.name, classType);
            }
        }

        // Sub-pass 2: Decide the type of variables.
        for (Declaration decl : program.declarations) {
            if ((decl instanceof VarDef) || (decl instanceof ConstVarDef))
            {
                Type t = decl.dispatch(this);
                sym.put(decl.getIdentifier().name, t);
            }
        }

        // Sub-pass 3: Recursively solve Func. Def. & Class Def.
        for (Declaration decl : program.declarations) {
            if (!(decl instanceof VarDef) && !(decl instanceof ConstVarDef))
            {
                Type t = decl.dispatch(this);
                sym.put(decl.getIdentifier().name, t);
            }

        }
        program.setSymbolTable(sym);
        return null;
    }


    private ValueType analyzeValueType(TypeAnnotation type) {
        ValueType returnType = ValueType.annotationToValueType(type);

        if (type instanceof ListType)
        {
            ListType t = (ListType) type;
            while (t.elementType instanceof ListType)
            {
                t = (ListType) t.elementType;
            }

            if (t.elementType instanceof ClassType) {
                String className = ((ClassType) t.elementType).className;
                if (!classTable.declare(className)) {
                    err(t.elementType, "Invalid type annotation; there is no class named: %s", className);
                }
            }
        }

        if (type instanceof ClassType) {
            String className = ((ClassType) type).className;
            if (!classTable.declare(className)) {
                err(type, "Invalid type annotation; there is no class named: %s", className);
            } else {
                returnType = (ClassValueType) classTable.get(className);
            }
        }
        return returnType;
    }


    @Override
    public Type analyze(VarDef varDef) {
        return analyzeValueType(varDef.var.type);
    }

    @Override
    public Type analyze(ConstVarDef varDef) {
        return analyzeValueType(varDef.var.type);
    }

    @Override
    public Type analyze(GlobalDecl decl) {
        Type t = sym.declareGlobal(decl.variable.name);
        if (t == null) {
            err(decl.variable, "Not a global variable: %s", decl.variable.name);
            return null;
        }
        return t;
    }

    @Override
    public Type analyze(NonLocalDecl decl) {
        Type t = sym.declareNonlocal(decl.variable.name);
        if (t == null) {
            err(decl.variable, "Not a nonlocal variable: %s", decl.variable.name);
            return null;
        }

        return t;
    }


    @Override
    public Type analyze(ClassDef classDef) {
        // set super-class type
        Type superClassType = Type.OBJECT_TYPE;
        if ((classTable.declare(classDef.superClass.name) && !classTable.get(classDef.superClass.name).isSpecialType())) {
            superClassType = classTable.get(classDef.superClass.name);
        }

        // retrieve the classType from classTable
        ClassValueType classType = (ClassValueType) classTable.get(classDef.name.name);
        classType.setSuperClassType((ClassValueType) superClassType); // set super-class

        // add attributes to memberTable
        for (Declaration decl : classDef.declarations) {
            if (classType.memberTable.declareOwn(decl.getIdentifier().name)) {
                err(decl.getIdentifier(), "Duplicate declaration of identifier in same scope: %s", decl.getIdentifier().name);
                continue;
            } else if (classType.memberTable.declare(decl.getIdentifier().name)
                    && (classType.memberTable.get(decl.getIdentifier().name) instanceof ClassValueType || decl instanceof VarDef || decl instanceof ConstVarDef)) {
                err(decl.getIdentifier(), "Cannot re-define attribute: %s", decl.getIdentifier().name);
                continue;
            }
            Type t = decl.dispatch(this);
            String declName = decl.getIdentifier().name;
            // check override func type
            if (decl instanceof FuncDef
                    && classType.memberTable.declare(declName)
                    && classType.memberTable.get(declName) instanceof FuncType
                    && !((FuncType) t).canOverride(classType.memberTable.get(declName))) {
                err(decl.getIdentifier(), "Method overridden with different type signature: %s", decl.getIdentifier().name);
                continue;
            }

            // check member func first param type
            if (decl instanceof FuncDef && (((FuncType) t).parameters.size() == 0 || !((FuncType) t).parameters.get(0).equals(classType))) {
                err(decl.getIdentifier(), "First parameter of the following method must be of the enclosing class: %s", decl.getIdentifier().name);
            }

            classType.memberTable.put(decl.getIdentifier().name, t);
        }

        return classType;
    }

    @Override
    public Type analyze(FuncDef funcDef) {

        SymbolTable<Type> tmpSym = sym; // save scope
        sym = new SymbolTable<>(sym); // create a new scope

        // process params
        List<ValueType> valueTypeList = new ArrayList<>(); // for forming the return value
        for (TypedVar p : funcDef.params) {
            // test shadowing
            if (classTable.declare(p.identifier.name)) {
                err(p, "Cannot shadow class name: %s", p.identifier.name);
                continue;
            }
            // test duplication
            if (sym.declareOwn(p.identifier.name)) {
                err(p, "Duplicate declaration of identifier in same scope: %s", p.identifier.name);
            }
            ValueType paramType = analyzeValueType(p.type);
            valueTypeList.add(paramType);
            sym.put(p.identifier.name, paramType);
        }

        // Sub-pass 1: Scan all the definition and detect potention & invalid duplicate def.
        for (Declaration decl : funcDef.declarations) {
            if (sym.declareOwn(decl.getIdentifier().name)) {
                err(decl.getIdentifier(), "Duplicate declaration of identifier in same scope: %s", decl.getIdentifier().name);
            }
            // test shadowing
            boolean isNotGlobalNonlocal = !(decl instanceof GlobalDecl) && !(decl instanceof NonLocalDecl);
            if (classTable.declare(decl.getIdentifier().name) && isNotGlobalNonlocal) {
                // Remark: for global & nonlocal, we do not test shadow class name
                err(decl.getIdentifier(), "Cannot shadow class name: %s", decl.getIdentifier().name);
            } else {
                // All symbols are set to OBJECT_TYPE temporarilty to detect duplication.
                // (Remark: except for global / nonlocal decl., see following branches for details)
                // Their type would be decided on Sub-pass 2
                if (decl instanceof VarDef) {
                    sym.put(decl.getIdentifier().name, Type.OBJECT_TYPE); // temporarily set to object
                }

                if (decl instanceof ConstVarDef)
                {
                    sym.put(decl.getIdentifier().name, Type.OBJECT_TYPE);
                    sym.setConst(decl.getIdentifier().name);
                }

                if (decl instanceof FuncDef) {
                    sym.put(decl.getIdentifier().name, new FuncType(Type.OBJECT_TYPE));
                }

                // Global / Nonlocal Decl's symbol type is well-defined in parent scope.
                // Try to retrieve the type by dispatching. 
                // Assign it to current symbol table if poosible.
                if (decl instanceof GlobalDecl)
                {
                    Type t = decl.dispatch(this);
                    if (t != null)
                    {
                        sym.put(decl.getIdentifier().name, t);
                        sym.setGlobal(decl.getIdentifier().name);
                    }
                }
                if (decl instanceof NonLocalDecl)
                {
                    Type t = decl.dispatch(this);
                    if (t != null)
                    {
                        sym.put(decl.getIdentifier().name, t);
                        sym.setNonlocal(decl.getIdentifier().name);
                    }

                }
            }
        }

        // Sub-pass 2: Decide the type of variables.
        for (Declaration decl : funcDef.declarations) {
            boolean isVar = (decl instanceof VarDef) || (decl instanceof ConstVarDef);
            if (!classTable.declare(decl.getIdentifier().name) && isVar) {
                Type t = decl.dispatch(this);
                sym.put(decl.getIdentifier().name, t);
            }
        }


        // Sub-pass 3: Recursively solve Func. Def.
        for (Declaration decl : funcDef.declarations) {
            if (!classTable.declare(decl.getIdentifier().name) && (decl instanceof FuncDef)) {
                Type t = decl.dispatch(this);
                sym.put(decl.getIdentifier().name, t);
            }
        }


        // process return type
        ValueType r = analyzeValueType(funcDef.returnType);

        funcDef.setSymbolTable(sym); // store the entry of scope to the node

        sym = tmpSym; // switch back to the outer scope

        return new FuncType(valueTypeList, r);
    }
}
