package chocopy.common.astnodes;

import chocopy.common.analysis.NodeAnalyzer;
import java_cup.runtime.ComplexSymbolFactory.Location;

import java.util.List;

/** A class definition. */
public class ClassDef extends Declaration {
    /** Name of the declared class. */
    public final Identifier name;
    /** Name of the parent class. */
    public final Identifier superClass;
    /** Body of the class. */
    public final List<Declaration> declarations;

    /** AST for `class NAME(SUPERCLASS): DECLARATIONS` spanning source locations [LEFT..RIGHT]. */
    public ClassDef(
            Location left,
            Location right,
            Identifier name,
            Identifier superClass,
            List<Declaration> declarations) {
        super(left, right);
        this.name = name;
        this.superClass = superClass;
        this.declarations = declarations;
    }

    public <T> T dispatch(NodeAnalyzer<T> analyzer) {
        return analyzer.analyze(this);
    }

    @Override
    public Identifier getIdentifier() {
        return this.name;
    }
}
