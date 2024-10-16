package chocopy.common.analysis;

import chocopy.common.astnodes.*;

/**
 * This interface can be used to separate logic for various concrete classes in the AST class
 * hierarchy.
 *
 * <p>The idea is that a phase of the analysis is encapsulated in a class that implements this
 * interface, and contains an overriding of the analyze method for each concrete Node class that
 * needs something other than default processing.
 *
 * <p>Each concrete node class, C, implements a generic dispatch method that takes a NodeAnalyzer<T>
 * argument and calls the overloading of analyze that takes an argument of type C. The effect is
 * that anode.dispatch(anAnalyzer) executes the method anAnalyzer.analyze that is appropriate to
 * aNode's dynamic type.
 *
 * <p>As a result, each NodeAnalyzer subtype encapsulates all implementations of a particular action
 * on Nodes. Thus, it inverts the usual OO pattern in which the implementations of analysis A for
 * each different class are scattered among the class bodies themselves as overridings of a method A
 * on the Node class.
 *
 * <p>The class AbstractNodeAnalyzer provides empty default implementations for these methods.
 *
 * <p>The type T is the type of result returned by the encapsulated analysis.
 */
public interface NodeAnalyzer<T> {

    T analyze(AssignStmt node);

    T analyze(BinaryExpr node);

    T analyze(BooleanLiteral node);

    T analyze(CallExpr node);

    T analyze(ClassDef node);

    T analyze(ClassType node);

    T analyze(CompilerError node);

    T analyze(Errors node);

    T analyze(ExprStmt node);

    T analyze(ForStmt node);

    T analyze(FuncDef node);

    T analyze(GlobalDecl node);

    T analyze(Identifier node);

    T analyze(IfExpr node);

    T analyze(IfStmt node);

    T analyze(IndexExpr node);

    T analyze(IntegerLiteral node);

    T analyze(ListExpr node);

    T analyze(ForListExpr node);

    T analyze(ListType node);

    T analyze(MemberExpr node);

    T analyze(MethodCallExpr node);

    T analyze(NoneLiteral node);

    T analyze(NonLocalDecl node);

    T analyze(Program node);

    T analyze(ReturnStmt node);

    T analyze(StringLiteral node);

    T analyze(TypedVar node);

    T analyze(UnaryExpr node);

    T analyze(VarDef node);

    T analyze(ConstVarDef node);

    T analyze(WhileStmt node);

    /**
     * Sets the default value returned by calls to analyze that are not overridden to VALUE. By
     * default, this is null.
     */
    void setDefault(T value);

    /** Default value for non-overridden methods. */
    T defaultAction(Node node);
}
