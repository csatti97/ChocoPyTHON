package chocopy.pa1;

import chocopy.common.astnodes.Program;
import java_cup.runtime.ComplexSymbolFactory;

import java.io.StringReader;

/** Interface between driver and parser. */
public class StudentParser {
    /** Returns PROGRAM resulting from parsing INPUT. Turn on parser debugging iff DEBUG. */
    public static Program process(String input, boolean debug) {
        ChocoPyLexer lexer = new ChocoPyLexer(new StringReader(input));
        ChocoPyParser parser = new ChocoPyParser(lexer, new ComplexSymbolFactory());
        return parser.parseProgram(debug);
    }
}
