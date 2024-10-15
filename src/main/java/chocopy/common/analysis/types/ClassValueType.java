package chocopy.common.analysis.types;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.ClassType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** Represents the semantic value of a simple class reference. */
public class ClassValueType extends ValueType {

    /** The name of the class. */
    private final String className;

    @JsonIgnore
    public  ClassValueType superClassType = Type.OBJECT_TYPE;

    @JsonIgnore
    public SymbolTable<Type> memberTable = new SymbolTable<>();

    @JsonIgnore
    public  void setSuperClassType(ClassValueType su){
        this.superClassType = su;
        this.memberTable = new SymbolTable<>(su.memberTable);
    }

    /** A class type for the class named CLASSNAME. */
    @JsonCreator
    public ClassValueType(@JsonProperty String className) {
        this.className = className;
    }

    /** A class type for the class referenced by CLASSTYPEANNOTATION. */
    public ClassValueType(ClassType classTypeAnnotation) {
        this.className = classTypeAnnotation.className;
    }

    /** this class <= t */
    @JsonIgnore
    public boolean isSubClassOf(ClassValueType t) {
        if (className.equals(t.className)) {
            return true;
        } else {
            if (this.equals(Type.OBJECT_TYPE)) {
                return false;
            } else {
                return superClassType.isSubClassOf(t);
            }
        }
    }



    @Override
    @JsonProperty
    public String className() {
        return className;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassValueType classType = (ClassValueType) o;
        return Objects.equals(className, classType.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }

    @Override
    public String toString() {
        return className;
    }
}
