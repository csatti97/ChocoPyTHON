package chocopy.common.analysis.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Representation for the static type of symbols and expressions during type-checking.
 *
 * <p>Symbols such as variables and attributes will typically map to a {@link ValueType}.
 *
 * <p>Symbols such as classes will typically map to a more complex Type.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
    @JsonSubTypes.Type(FuncType.class),
    @JsonSubTypes.Type(ClassValueType.class),
    @JsonSubTypes.Type(ListValueType.class)
})
public abstract class Type {

    /** The type object. */
    public static final ClassValueType OBJECT_TYPE = new ClassValueType("object");
    /** The type int. */
    public static final ClassValueType INT_TYPE = new ClassValueType("int");
    /** The type str. */
    public static final ClassValueType STR_TYPE = new ClassValueType("str");
    /** The type bool. */
    public static final ClassValueType BOOL_TYPE = new ClassValueType("bool");

    /** The type of None. */
    public static final ClassValueType NONE_TYPE = new ClassValueType("<None>");
    /** The type of []. */
    public static final ClassValueType EMPTY_TYPE = new ClassValueType("<Empty>");

    /** Place holder for global and nonlocal variables in symbol table*/
/*    public static final ClassValueType GLOBAL_TYPE = new ClassValueType("<Global>");
    public static final ClassValueType NONLOCAL_TYPE = new ClassValueType("<Nonlocal>");
*/
    static {
        // add __init__ to object
        List<ValueType> objectInitParamList = new ArrayList<>();
        objectInitParamList.add(Type.OBJECT_TYPE);
        FuncType objectInit = new FuncType(objectInitParamList,Type.NONE_TYPE);
        Type.OBJECT_TYPE.memberTable.put("__init__", objectInit);
    }


    /** Returns the name of the class if this is a class type; otherwise null. */
    public String className() {
        return null;
    }

    /** Returns true iff this is a type that does not include the value None. */
    @JsonIgnore
    public boolean isSpecialType() {
        return equals(INT_TYPE) || equals(BOOL_TYPE) || equals(STR_TYPE);
    }

    @JsonIgnore
    public boolean isListType() {
        return false;
    }

    @JsonIgnore
    public boolean isFuncType() {
        return false;
    }

    /** Returns true iff this type represents a kind of assignable value. */
    @JsonIgnore
    public boolean isValueType() {
        return false;
    }

    /** For list types, returns the type of the elements; otherwise null. */
    @JsonIgnore
    public ValueType elementType() {
        return null;
    }

    @JsonIgnore
    public static Type getLowestCommonAncestor(Type t1, Type t2) {

        if (t1.canBeAssignedTo(t2)) return t2;
        if (t2.canBeAssignedTo(t1)) return t1;

        // find common ancestor
        if (t1 instanceof ClassValueType && t2 instanceof ClassValueType) {
            assert t1.isSubClassOf(Type.OBJECT_TYPE) && t2.isSubClassOf(Type.OBJECT_TYPE);

            ClassValueType ct1 = (ClassValueType)t1, ct2 = (ClassValueType)t2;
            List<ClassValueType> ct1ParentList = new LinkedList<>();
            while (true) {
                ct1ParentList.add(ct1);
                if (ct1.equals(Type.OBJECT_TYPE)) {
                    break;
                }
                ct1 = ct1.superClassType;
            }

            while (true) {
                if (ct1ParentList.contains(ct2)) {
                    return ct2;
                }
                ct2 = ct2.superClassType;
            }
        }

        return Type.OBJECT_TYPE;
    }

    // t1 = <Empty>, t2 = [T]
    private static boolean emptyTypeAndList(Type t1, Type t2) {
        return (t1.equals(Type.EMPTY_TYPE) && t2 instanceof ListValueType);
    }

    // t1 = <None>, t2 = !int && !bool && !str
    private static boolean noneTypeAndNonIntBoolStr(Type t1, Type t2) {
        return t1.equals(Type.NONE_TYPE) && (!t2.equals(Type.INT_TYPE) && !t2.equals(Type.BOOL_TYPE) && !t2.equals(Type.STR_TYPE));
    }

    //t1 is [<None>], t2 is [!int && !str && !bool]
    private static boolean noneListAndList(Type t1, Type t2) {
        return t1 instanceof ListValueType
                && ((ListValueType) t1).elementType.equals(Type.NONE_TYPE)
                && t2 instanceof ListValueType
                && !((ListValueType) t2).elementType.equals(Type.INT_TYPE)
                && !((ListValueType) t2).elementType.equals(Type.BOOL_TYPE)
                && !((ListValueType) t2).elementType.equals(Type.STR_TYPE);
    }

    @JsonIgnore
    public boolean isSubClassOf(Type t) {
        if (this.equals(t)) {
            return true;
        } else if (t.equals(Type.OBJECT_TYPE)) {
            return true;
        } else if (this instanceof ClassValueType && t instanceof ClassValueType) {
            return ((ClassValueType)this).isSubClassOf((ClassValueType)t);
        } else {
            return false;
        }
    }

    @JsonIgnore
    public boolean canBeAssignedTo(Type t) {
        return isSubClassOf(t)
                || noneTypeAndNonIntBoolStr(this, t)
                || emptyTypeAndList(this, t)
                || noneListAndList(this, t);
    }
}
