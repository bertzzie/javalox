# Lox Interpreter

A simple implementation of Lox interpreter from the [CraftingInterpreter](http://craftinginterpreters.com) book.

This is an exercise repo, meant to implement what's in the book with some addition:

- All challenges completed
- Better repl with syntax highlighting, etc
- Language Server to integrate with editors / IDE
- Static analysis tool

## Building and Running

After cloning the repo, execute:

```$bash
$ mvn package
```

to generate an executable jar in the `target` directory. 
You can then run the file:

```$bash
$ java -jar target/lox-0.0.1-SNAPSHOT.jar
```

to see the REPL.

File and build system is not really supported for now,
but the foundation to do so is there.

## Current State

Right now the interpreter can:

- parse and execute basic statements (arithmetic, logical operation)
- assign global variable
- assign variable in block scope
- execute ternary operator
- flow control via if statement
- looping via for and while statements

More will come:

- [ ] Update REPL to support statement. Rule is: execute statement, print result of expression. 
- [ ] Throws a runtime error on uninitialized variable
- [ ] Create an exact rule for global / local scoping, i.e.:
    
        var a = 1;
        {
          var a = a + 2;
          print a;
        }
    
    in C# this will give an error. C will prints 2 because that inside `a` counts as uninitialized and uninitialized is treated as `null`, and `int` + `null` is `int`.
    
- [x] Integrate build and packaging system (maven? gradle?)
- [x] Implement control flow (if, while, for)
- [x] Implement break statement for looping
- [x] Implement function and make sure it's first-class value 
- [ ] Upgrade variable / function binding and resolving
- [ ] Implement Basic Class (OOP)
- [ ] Implement Inheritance
- [ ] Implmenet ADT
- [ ] Learn about VM and implement VM (likely in C or Rust)