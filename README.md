# Lox Interpreter

A simple implementation of Lox interpreter from the [CraftingInterpreter](http://craftinginterpreters.com) book.

This is an exercise repo, meant to implement what's in the book with some addition:

- All challenges completed
- Better repl with syntax highlighting, etc
- Language Server to integrate with editors / IDE
- Static analysis tool

## Current State

Right now the interpreter can:

- parse and execute statements
- assign global variable

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
    
- [ ] Implement control flow (if, while, for)
- [ ] Implement function and make sure it's first-class value 
- [ ] Upgrade variable / function binding and resolving
- [ ] Implement Basic Class (OOP)
- [ ] Implement Inheritance
- [ ] Implmenet ADT
- [ ] Learn about VM and implement VM (likely in C or Rust)