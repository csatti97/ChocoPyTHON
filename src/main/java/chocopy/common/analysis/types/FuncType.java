package chocopy.common.analysis.types;

import chocopy.common.astnodes.TypedVar;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.ArrayList;
import java.util.List;

/** Semantic information for a function or method. */
public class FuncType extends Type {

    /** Types of parameters. */
    public final List<ValueType> parameters;
    /** Function's return type. */
    public final ValueType returnType;

    /** Creates a FuncType returning RETURNTYPE0, initially parameterless. */
    public FuncType(ValueType returnType0) {
        this(new ArrayList<>(), returnType0);
    }

    /**
     * Creates a FuncType for NAME0 with formal parameter types PARAMETERS0, returning type
     * RETURNTYPE0.
     */
    @JsonCreator
    public FuncType(List<ValueType> parameters0, ValueType returnType0) {
        this.parameters = parameters0;
        this.returnType = returnType0;
    }

    @Override
    public boolean isFuncType() {
        return true;
    }

    /** Returns the type of the K-th parameter. */
    public ValueType getParamType(int k) {
        return parameters.get(k);
    }

    @Override
    public String toString() {
        return "<function>";
    }

    public boolean canOverride(Type targetType) {
        if (targetType instanceof FuncType) {
            FuncType targetFuncType = (FuncType) targetType;
            if (parameters.size() == (targetFuncType).parameters.size()) { // check same parameter number
                // check param type from the 2nd param (first parameter type is its own class type)
                for (int i = 1; i < parameters.size(); i++) {
                    if (!parameters.get(i).equals(targetFuncType.parameters.get(i))) {
                        return false;
                    }
                }
                return returnType.equals(targetFuncType.returnType);
            }
        }
        return false;
    }
}
