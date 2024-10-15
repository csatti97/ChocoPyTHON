package chocopy.pa1;
import java_cup.runtime.*;
import java.util.*;

%%

/* Configuration begins. */

%unicode
%line
%column

%class ChocoPyLexer
%public

%cupsym ChocoPyTokens
%cup
%cupdebug

%eofclose false



/* Configuration ends. */

/* The following code section is copied verbatim to the generated lexer class. */
%{

    final boolean debug_flag = false;

    private void println(Object o){
        if (debug_flag)
            System.out.println(o);
    }

    /* The code below includes some convenience methods to create tokens
     * of a given type and optionally a value that the CUP parser can
     * understand. Specifically, a lot of the logic below deals with
     * embedded information about where in the source code a given token
     * was recognized, so that the parser can report errors accurately. */

    public Queue<Symbol> tokenQ = new LinkedList<Symbol>();

    public Symbol myGetNextToken() throws java.io.IOException{
        if (tokenQ.size() > 0) {
            return tokenQ.poll();
        } else {
            Symbol t = next_token();
            addToken(t);
            return tokenQ.poll();
        }
    }

    private void addToken(Symbol s) {
        addIndentation(s);
        println(ChocoPyTokens.terminalNames[s.sym]);
        tokenQ.add(s);
    }

    private void addIndentation(Symbol s) {
        if (indent_state == 1) {
            indent_state = 2;
            /* INDENT  */
            if (indent_length > 0 && (indent_stack.size() == 0 || indent_stack.peek() < indent_length)) {
                indent_stack.push(indent_length);
                addToken(symbol_dent(ChocoPyTokens.INDENT));
            /* DEDENT  */
            } else if (indent_stack.size() > 0 && indent_length < indent_stack.peek() && (indent_stack.contains(indent_length) || indent_length == 0)) {
                while (indent_stack.size() > 0 && indent_length < indent_stack.peek()) {
                    indent_stack.pop();
                    addToken(symbol_dent(ChocoPyTokens.DEDENT));
                }
            /* ERROR  */
            } else if (indent_stack.size() > 0 && !indent_stack.contains(indent_length) && indent_length != 0) {
                addToken(symbol(ChocoPyTokens.INDENTERROR));
            }
        } else {
            if (indent_state == 2) {
                /* reset indentation detection state*/
                if (s.sym == ChocoPyTokens.NEWLINE) {
                    indent_state = 1;
                    indent_length = 0;
                /* clear stack */
                } else if (s.sym == ChocoPyTokens.EOF) {
                    while (indent_stack.size() > 0) {
                        indent_stack.pop();
                        addToken(symbol_dent(ChocoPyTokens.DEDENT));
                    }
                }
            }
        }
    }

    /* Indentation stack  */
    Stack<Integer> indent_stack = new
    Stack<Integer>();

    /* indent state  */
    int indent_state = 1; // 1: line start, 2:line has content
    int indent_length = 0; // indentation length caused by last whitespace

    /* compute the next indent length that is a multiple of tabSize */
    final int tabSize = 8;
    private int nextTabSizeMultiple(int current) {
        return tabSize * (current / tabSize + (current % tabSize > 0 ? 1 : 0));
    }

    /* compute indent length from whitespaces */
    private int computeIndentLength(String text) {
        int ans = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                ans += 1;
            } else {
                ans = nextTabSizeMultiple(ans + 1);
            }
        }
        return ans;
    }

    /* String Literal */
    String str_ltrl = "";
    int str_ltrl_st_col = 1; // the start column of string literal

    /** Producer of token-related values for the parser. */
    final ComplexSymbolFactory symbolFactory = new ComplexSymbolFactory();

    /**
     * Returns a terminal symbol of syntactic category INDENT/DEDENT
     * and no semantic value at the location decided by indent stack
     */
    private Symbol symbol_dent(final int type) {
        return symbolFactory.newSymbol(
            ChocoPyTokens.terminalNames[type],
            type,
            new ComplexSymbolFactory.Location(yyline + 1, 1),
            new ComplexSymbolFactory.Location(yyline + 1, indent_length),
            null
        );
    }

    /**
     * Returns a terminal symbol of syntactic category TYPE
     * and no semantic value at the current source location.
     */
    private Symbol symbol(final int type) {
        return symbol(type, yytext());
    }

    /**
     * Returns a terminal symbol of syntactic category TYPE
     * and semantic value VALUE at the current source location.
     */
    private Symbol symbol(final int type, final Object value) {
        return symbolFactory.newSymbol(
            ChocoPyTokens.terminalNames[type],
            type,
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + 1),
            new ComplexSymbolFactory.Location(yyline + 1, yycolumn + yylength()),
            value);
    }

    private Symbol symbol(final int type, final Object value, int st_line, int st_col, int end_line, int end_col) {
            return symbolFactory.newSymbol(
                ChocoPyTokens.terminalNames[type],
                type,
                new ComplexSymbolFactory.Location(st_line, st_col),
                new ComplexSymbolFactory.Location(end_line, end_col),
                value);
    }

%}

/* Macros (regexes used in rules below). */


WhiteSpace = [ \t]+
LineBreak = [ \t]*\R

IntegerLiteral = 0 | [1-9][0-9]*
BooleanLiteral = "True" | "False"
NoneLiteral = "None"
Identifier = [_a-zA-Z][_a-zA-Z0-9]*
IdString = "\""[_a-zA-Z][_a-zA-Z0-9]*"\""
Comment = "#"[^\r\n]*

ClassType = "int"|"bool"|"object"|"str"|"float"
DCLR = (("const"[ ]*)|"")[_a-zA-Z][_a-zA-Z0-9]*[ ]*:[ ]*{ClassType}[ ]*"="
// Remark: Use TypeID at lexical level to avoid shift/reduce conflict at syntax anaylsis

RESERVE = "import"|"as"|"assert"|"async"|"await"|"break"|"continue"|"del"|"except"|"finally"|"from"|"lambda"|"raise"|"try"|"with"|"yield"

%state DCLRVAR
%state STRING

%%



<YYINITIAL> {
  //{ClassType}                 { return symbol(ChocoPyTokens.CLASSTYPE); }
  {RESERVE}                   { return symbol(ChocoPyTokens.RESERVED); }

  "const"                     { return symbol(ChocoPyTokens.CONST); }

  /* Global & Local definition */
  "global"                    { return symbol(ChocoPyTokens.GLOBAL); }
  "nonlocal"                  { return symbol(ChocoPyTokens.NONLOCAL); }

  /* Function definition. */
  "def"                       { return symbol(ChocoPyTokens.FUNCDEF); }
  "->"                        { return symbol(ChocoPyTokens.FUNCRETARROW); }
  "return"                    { return symbol(ChocoPyTokens.FUNCRETURN); }
  "pass"                      { return symbol(ChocoPyTokens.PASS); }

  /* Class definition */
  "class"                     { return symbol(ChocoPyTokens.CLASSDEF);  }
  "\."                        { return symbol(ChocoPyTokens.DOT);  }

  /* For-loop statement. e.g: for x in []*/
  "for"                       { return symbol(ChocoPyTokens.FOR); }
  "in"                        { return symbol(ChocoPyTokens.IN); }
  "while"                     { return symbol(ChocoPyTokens.WHILE); }

  /* Delimiters. */
  {LineBreak}                 {
                                if (indent_state == 2) {
                                    // System.out.println("state == 2, generate NEWLINE");
                                    return symbol(ChocoPyTokens.NEWLINE);
                                }
                              }

  ","                         { return symbol(ChocoPyTokens.COMMA); }
  ":"                         { return symbol(ChocoPyTokens.COLON); }

  /* Literals. */
  {IntegerLiteral}            { long n = Long.parseLong(yytext(), 10);
                                if (n >= Math.pow(2, 31)){
                                    return symbol(ChocoPyTokens.UNRECOGNIZED);
                                }
                                return symbol(ChocoPyTokens.NUMBER,
                                              Integer.parseInt(yytext())); }
  {BooleanLiteral}            { return symbol(ChocoPyTokens.BOOLEANS,
                                              Boolean.parseBoolean(yytext()));}

  "\""                        { str_ltrl = ""; str_ltrl_st_col = yycolumn+1; yybegin(STRING);}


  {NoneLiteral}               { return symbol(ChocoPyTokens.NONE); }


  /* Conditional Statement. */
  "if"                        { return symbol(ChocoPyTokens.IF, yytext()); }
  "elif"                      { return symbol(ChocoPyTokens.ELIF, yytext()); }
  "else"                      { return symbol(ChocoPyTokens.ELSE, yytext()); }


  /* Operators. */
  "+"                         { return symbol(ChocoPyTokens.PLUS, yytext()); }
  "-"                         { return symbol(ChocoPyTokens.MINUS, yytext()); }
  "*"                         { return symbol(ChocoPyTokens.TIMES, yytext()); }
  "//"                        { return symbol(ChocoPyTokens.DIVIDE, yytext()); }
  "%"                         { return symbol(ChocoPyTokens.MODULO, yytext()); }
  ">"                         { return symbol(ChocoPyTokens.GT, yytext()); }
  "<"                         { return symbol(ChocoPyTokens.LT, yytext()); }
  "<="                        { return symbol(ChocoPyTokens.LE, yytext()); }
  ">="                        { return symbol(ChocoPyTokens.GE, yytext()); }
  "not"                       { return symbol(ChocoPyTokens.NOT, yytext()); }
  "or"                        { return symbol(ChocoPyTokens.OR, yytext()); }
  "and"                       { return symbol(ChocoPyTokens.AND, yytext()); }
  "is"                        { return symbol(ChocoPyTokens.IS, yytext()); }

  /* Assignment */
//  {DCLR}                      {
//                                // add a dummy terminal to solve
//                                // shift/reduce conflict
//                                yypushback(yytext().length());
//                                yybegin(DCLRVAR);
//                                return symbol(ChocoPyTokens.DCLRVARSTART);
//                              }

  "=="                        { return symbol(ChocoPyTokens.LOGIEQ, yytext()); }
  "!="                        { return symbol(ChocoPyTokens.NOTEQ, yytext()); }
  "="                         { return symbol(ChocoPyTokens.EQ, yytext()); }

  /* Left & Right parenthesis */
  "("                         { return symbol(ChocoPyTokens.LPAR, yytext()); }
  ")"                         { return symbol(ChocoPyTokens.RPAR, yytext()); }

  /* Square Bracket */
  "["                         { return symbol(ChocoPyTokens.LBRKT, yytext()); }
  "]"                         { return symbol(ChocoPyTokens.RBRKT, yytext()); }


  /* Identifier. */
  {Identifier}                { return symbol(ChocoPyTokens.IDENTIFIER, yytext()); }
  {IdString}                  { return symbol(ChocoPyTokens.IDSTRING, yytext().substring(1, yytext().length()-1), yyline+1, yycolumn+1, yyline+1, yycolumn+1+yytext().length()-1); }
  /* Whitespace. */
  {WhiteSpace}                { indent_length = computeIndentLength(yytext()); }
  {Comment}                   { } //System.out.println("Comment--"+yytext()+"--");
}



//<DCLRVAR> {
//  "const"                     { return symbol(ChocoPyTokens.CONST); }
//  {ClassType}                 { return symbol(ChocoPyTokens.CLASSTYPE); }
//  {Identifier}                { return symbol(ChocoPyTokens.IDENTIFIER, yytext()); }
//  ":"                         { return symbol(ChocoPyTokens.COLON);  }
//  "="                         { yybegin(YYINITIAL); return symbol(ChocoPyTokens.EQ, yytext()); }
//  {WhiteSpace}                { /* Ignore. */ }
//}

<STRING> {
  "\\t"                        { str_ltrl = str_ltrl + "\t"; }
  "\\\\"                       { str_ltrl = str_ltrl + "\\"; }
  "\\n"                        { str_ltrl = str_ltrl + "\n"; }
  "\\\""                       { str_ltrl = str_ltrl + "\""; }
  "\""                         { yybegin(YYINITIAL); return symbol(ChocoPyTokens.STRING, str_ltrl, yyline+1, str_ltrl_st_col, yyline+1, yycolumn+1 ); }
  .                            { str_ltrl = str_ltrl + yytext(); }
}


<<EOF>>                       { return symbol(ChocoPyTokens.EOF); }

/* Error fallback. */
[^]                           { return symbol(ChocoPyTokens.UNRECOGNIZED); }
