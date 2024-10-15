package chocopy.common.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import chocopy.common.analysis.types.Type;
import chocopy.common.analysis.types.FuncType;

/**
 * A block-structured symbol table mapping identifiers to information about them of type T in a
 * given declarative region.
 */
public class SymbolTable<T> {
    /** Contents of the current (innermost) region. */
    private final Map<String, T> table = new HashMap<>();
    /** Whether is constant. */
    private final Set<String> constvar = new HashSet<>();
    /** Whether is global. */
    private final Set<String> globalvar = new HashSet<>();
    /** Whether is nonlocal. */
    private final Set<String> nonlocalvar = new HashSet<>();

    /** Enclosing block. */
    private final SymbolTable<T> parent;

    /** A table representing a region nested in that represented by PARENT0. */
    public SymbolTable(SymbolTable<T> parent0) {
        parent = parent0;
    }

    /** A top-level symbol table. */
    public SymbolTable() {
        this.parent = null;
    }

    public void print() {
        System.out.println("symbolTable printOwn: ");
        for (String s: table.keySet()){
            System.out.print(s + "->" + table.get(s).toString());
            if (isGlobal(s))
            {
                System.out.print("(global)");
            }
            System.out.print(";");
        }
        System.out.println();
    }

    public void printAll() {
        System.out.println("symbolTable printAll: ");
        SymbolTable<T> sym = this;
        while (sym != null) {
            for (String s: sym.table.keySet()){
                System.out.print(s + "->" + sym.table.get(s).toString() + "; ");
            }
            sym = sym.parent;
        }
        System.out.println();
    }

    /** Returns the mapping of NAME in the innermost nested region containing this one. */
    public T get(String name) {
        // if (table.containsKey(name) && table.get(name) != Type.GLOBAL_TYPE && table.get(name) != Type.NONLOCAL_TYPE) {
        if (table.containsKey(name)) {
            return table.get(name);
        } else if (parent != null) {
            return parent.get(name);
        } else {
            return null;
        }
    }

    /**
     * Adds a new mapping of NAME -> VALUE to the current region, possibly shadowing mappings in the
     * enclosing parent. Returns modified table.
     */
    public SymbolTable<T> put(String name, T value) {
        table.put(name, value);
        return this;
    }

    public SymbolTable<T> remove(String name) {
        table.remove(name);
        if (isGlobal(name))
        {
            globalvar.remove(name);
        }
        if (isNonlocal(name))
        {
            nonlocalvar.remove(name);
        }

        return this;
    }

    public boolean isConst(String name)
    {
        return constvar.contains(name);
    }

    public void setConst(String name)
    {
        constvar.add(name);
    }

    public boolean isGlobal(String name)
    {
        return globalvar.contains(name);
    }

    public void setGlobal(String name)
    {
        globalvar.add(name);
    }

    public boolean isNonlocal(String name)
    {
        return nonlocalvar.contains(name);
    }

    public void setNonlocal(String name)
    {
        nonlocalvar.add(name);
    }


    /** Returns whether NAME has a mapping in global. */
    public Type declareGlobal(String name) {
        SymbolTable<T> globalSym = this;
        while(globalSym.parent != null) {
            globalSym = globalSym.parent;
        }
        if (globalSym.table.containsKey(name))
        {
            Type t = (Type) globalSym.table.get(name);
            if (t instanceof FuncType)
            {
                return null;
            }

            // a variable same as the class name in global would be placed
            // in case of re-definition of a class (see Program anaylze in DeclarationAnayzer.java)
            if (t.className().equals(name))
            {
                return null;
            }
            return t;
        }

        return null;
    }

    /** Returns whether NAME has a mapping in nonlocal. */
    public Type declareNonlocal(String name) {
        SymbolTable<T> sym = parent;
        if (sym == null)
        {
            return null;
        }
        while(sym.parent != null) {
            Type t = (Type) sym.table.get(name);
            if (sym.table.containsKey(name) && !isSpecialType(t))
            {
                if (sym.isGlobal(name))
                {
                    return null;
                }
                if (!sym.isNonlocal(name))
                {
                    return t;
                }
            }
            sym = sym.parent;
        }
        return null;
    }

    /** Returns whether NAME has a mapping in this region (ignoring enclosing regions. */
    public boolean declareOwn(String name) {
        return table.containsKey(name);
    }

    /** Returns whether NAME has a mapping in this region and enclosing regions. */
    public boolean declare(String name) {
        T t = get(name);
        return t != null;
    }

    /** Returns all the names declared this region (ignoring enclosing regions). */
    public Set<String> getDeclaredSymbols() {
        return table.keySet();
    }

    /** Returns the parent, or null if this is the top level. */
    public SymbolTable<T> getParent() {
        return this.parent;
    }

    public boolean isSpecialType(Type t) {
        return t instanceof FuncType;
    }

}
