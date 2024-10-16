package chocopy.common.analysis.types;

import chocopy.common.astnodes.ListType;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Objects;

/** Represents a semantic value of a list type denotation. */
public class ListValueType extends ValueType {

    /** This ListValueType represents [ELEMENTTYPE]. */
    public final ValueType elementType;

    /** Represents [ELEMENTTYPE]. */
    @JsonCreator
    public ListValueType(Type elementType) {
        this.elementType = (ValueType) elementType;
    }

    /** Represents [<type>], where <type> is that denoted in TYPEANNOTATION. */
    public ListValueType(ListType typeAnnotation) {
        elementType = ValueType.annotationToValueType(typeAnnotation.elementType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ListValueType tlo = (ListValueType) o;

        return Objects.equals(elementType, tlo.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementType);
    }

    @Override
    public String toString() {
        return "[" + elementType.toString() + "]";
    }

    /** Returns true iff represents [T]. */
    @Override
    public boolean isListType() {
        return true;
    }

    @Override
    public ValueType elementType() {
        return elementType;
    }
}
