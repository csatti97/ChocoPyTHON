# ChocoPy Compiler

This class project for NYU CSCI-GA.2130: Compiler Construction was adapted, with the original authors' permission, from the project developed at the University of California, Berkeley for [CS 164: Programming Languages and Compilers](https://www2.eecs.berkeley.edu/Courses/CS164/).

ChocoPy was designed by [Rohan Padhye](https://rohan.padhye.org/) and [Koushik Sen](https://people.eecs.berkeley.edu/~ksen), with substantial contributions from [Paul Hilfinger](https://www2.eecs.berkeley.edu/Faculty/Homepages/hilfinger.html):
> Rohan Padhye, Koushik Sen, and Paul N. Hilfinger. 2019. ChocoPy: A Programming Language for Compilers Courses. In Proceedings of the 2019 ACM SIGPLAN SPLASH-E Symposium (SPLASH-E ’19), October 25, 2019, Athens, Greece. ACM, New York, NY, USA, 5 pages. https://doi.org/10.1145/3358711.3361627

## Quickstart

Generate and compile your project:
```
mvn clean package
```

Run the tests (use `:` on Unix but `;` on Windows as a classpath separator):
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=s --test --dir src/test/data/pa1/sample/
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=.s --test --dir src/test/data/pa2/sample/
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=..s --test --run --dir src/test/data/pa3/sample/
```

With the starter code, only one test should pass in each part.

## Passes

`--pass` controls which implementation to use (student or reference) for which pass (parsing, semantic analysis, and code generation).
You can mix and match `s` and `r`, and use `.` to skip passes.
For example:
* `--pass=s` uses your parser.
* `--pass=.r` skips the first pass and uses the reference semantic analyzer.
  Because we skip parsing, we need to provide the semantic analyzer a preparsed `.py.ast` rather than raw `.py` input.
* `--pass=srs` uses your parser, the reference semantic analyzer, and your code generator.

## Options

With no extra options, the compiler outputs the actual result of the selected pass:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=r src/test/data/pa1/sample/expr_plus.py
```

Use `--out` to save the output to a file:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=r src/test/data/pa1/sample/expr_plus.py --out expr_plus.py.parsed
```

Use `--test` (on an individual file or a directory with `--dir`) to run in test mode, where your output is compared with that of the reference implementation:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=s --test --dir src/test/data/pa1/sample/
```

Use `--debug` to enable debugging output:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=r src/test/data/pa1/sample/expr_plus.py --debug
```

## Running RISC-V

The code generator outputs RISC-V assembly.
For example, you can fully compile a ChocoPy program to RISC-V with:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=rrr src/test/data/pa3/sample/op_add.py
```

Add `--run` to execute the generated assembly on the bundled RISC-V emulator:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=rrr --run src/test/data/pa3/sample/op_add.py
```

Add `--profile` to see how many CPU cycles your program took:
```
java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
  --pass=rrr --run --profile src/test/data/pa3/sample/op_add.py
```
