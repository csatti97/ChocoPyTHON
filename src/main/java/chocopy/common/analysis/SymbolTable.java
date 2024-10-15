package chocopy.common.analysis;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A block-structured symbol table mapping identifiers to information about them of type T in a
 * given declarative region.
 */
public class SymbolTable<T> {
    /** Contents of the current (innermost) region. */
    private final Map<String, T> table = new HashMap<>();
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

    /** Returns the mapping of NAME in the innermost nested region containing this one. */
    public T get(String name) {
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

    /** Returns whether NAME has a mapping in this region (ignoring enclosing regions. */
    public boolean declares(String name) {
        return table.containsKey(name);
    }

    /** Returns all the names declared this region (ignoring enclosing regions). */
    public Set<String> getDeclaredSymbols() {
        return table.keySet();
    }

    /** Returns the parent, or null if this is the top level. */
    public SymbolTable<T> getParent() {
        return this.parent;
    }
}
