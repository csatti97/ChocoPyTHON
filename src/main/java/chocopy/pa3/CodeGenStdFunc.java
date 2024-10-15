package chocopy.pa3;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.*;
import chocopy.common.codegen.*;
import chocopy.common.analysis.types.*;

import static chocopy.common.Utils.*;

import java.util.List;

import static chocopy.common.codegen.RiscVBackend.Register.*;

public class CodeGenStdFunc {

    public static void analyzeLen(RiscVBackend backend, CodeGenImpl base, CodeGenImpl.StmtAnalyzer analyzer, CallExpr expr){
        Expr e = expr.args.get(0);
        RiscVBackend.Register r = analyzer.getReturnReg(expr);
        RiscVBackend.Register valReg = analyzer.getFreeReg();
        Type etype = e.getInferredType();
        e.dispatch(analyzer);
        if (etype.equals(Type.NONE_TYPE) || etype.equals(Type.INT_TYPE) || etype.equals(Type.BOOL_TYPE)){
            backend.emitMV(A0, valReg, "Load arg");
            backend.emitJ(base.errorArg, "Go to error handler");
        } else if (etype.equals(Type.STR_TYPE) || etype instanceof ListValueType){
            backend.emitMV(A0, valReg, "Load argument");
            analyzer.SaveBusyReg();
            backend.emitJAL(new Label("$len"), "Invoke function: len");
            analyzer.LoadBusyReg();
            backend.emitMV(r, A0, "Move result to the return register");
        } else {
            System.out.println("[CallExpr] `len` not support type " + e.getInferredType());
        }
        analyzer.FreeReg(1);
    }

    public static void analyzePrint(RiscVBackend backend, CodeGenImpl base, CodeGenImpl.StmtAnalyzer analyzer, CallExpr expr) {
	    Expr e = expr.args.get(0);
	    RiscVBackend.Register valReg = analyzer.getFreeReg();
	    e.dispatch(analyzer);
	    if (e.getInferredType().equals(Type.NONE_TYPE)){
	        backend.emitMV(A0, valReg, "Load arg");
	        backend.emitJ(base.errorArg, "Go to error handler");
	    } else if (e.getInferredType().equals(Type.INT_TYPE)) {
	        backend.emitMV(A0, valReg, "Move to $a0 for boxing");
	        backend.emitJAL(base.makeint, "Box integer");
	        
	        backend.emitADDI(
	                SP, SP, -1 * backend.getWordSize(), "Push one argument (move $sp first).");
	        backend.emitSW(A0, SP, 0, "Push argument 0.");
	        backend.emitJAL(new Label("$print"), "Invoke function: print");
			backend.emitADDI(SP, SP, 1 * backend.getWordSize(), "Pop one argument");
	    } else if (e.getInferredType().equals(Type.OBJECT_TYPE)) {
	        backend.emitADDI(
	                SP, SP, -1 * backend.getWordSize(), "Push one argument (move $sp first).");
	        backend.emitSW(valReg, SP, 0, "Push argument 0.");
	        backend.emitJAL(new Label("$print"), "Invoke function: print");
			backend.emitADDI(SP, SP, 1 * backend.getWordSize(), "Pop one argument");
		} else if (e.getInferredType().equals(Type.BOOL_TYPE)){
	        backend.emitMV(A0, valReg, "Move to $a0 for boxing");
	        backend.emitJAL(base.makebool, "Box boolean");
	        backend.emitADDI(SP, SP, -1 * backend.getWordSize(), "Push one argument");
	        backend.emitSW(A0, SP, 0, "Push argument 0");
	        backend.emitJAL(new Label("$print"), "Invoke function: print");
			backend.emitADDI(SP, SP, 1 * backend.getWordSize(), "Pop one argument");
		} else if (e.getInferredType().equals(Type.STR_TYPE)){
	        backend.emitMV(A0, valReg, "Move to $a0 for boxing");
	        backend.emitADDI(SP, SP, - 1 * backend.getWordSize(), "Push one argument");
	        backend.emitSW(A0, SP, 0, "Push argument 0");
	        backend.emitJAL(new Label("$print"), "Invoke function: print");
			backend.emitADDI(SP, SP, 1 * backend.getWordSize(), "Pop one argument");
		} else {
	        System.out.println("[CallExpr] `print` not support " + e.getInferredType());
	    }

	    
	    analyzer.FreeReg(1);
	}

	public static void analyzeInput(RiscVBackend backend, CodeGenImpl base, CodeGenImpl.StmtAnalyzer analyzer, CallExpr expr) {
        RiscVBackend.Register retReg = analyzer.getReturnReg(expr);
        backend.emitJAL(new Label("$input"), "Invoke function: input");
        backend.emitMV(retReg, A0, "Move returned value to " + retReg);
    }

	public static void analyzeInt(RiscVBackend backend, CodeGenImpl codegen, CodeGenImpl.StmtAnalyzer analyzer, CallExpr expr) {
		RiscVBackend.Register retReg = analyzer.getReturnReg(expr);
		backend.emitMV(retReg, ZERO, "Move result of int(), 0 to " + retReg);
	}

	public static void analyzeBool(RiscVBackend backend, CodeGenImpl codegen, CodeGenImpl.StmtAnalyzer stmtAnalyzer, CallExpr expr) {
		RiscVBackend.Register retReg = stmtAnalyzer.getReturnReg(expr);
		backend.emitMV(retReg, ZERO, "Move result of bool(), 0 to " + retReg);
	}

	public static void analyzeStr(RiscVBackend backend, CodeGenImpl codegen, CodeGenImpl.StmtAnalyzer stmtAnalyzer, CallExpr expr) {
		backend.emitLA(stmtAnalyzer.getReturnReg(expr), codegen.getConstants().getStrConstant(""), "Load value of str(), \"\"");
	}
}
