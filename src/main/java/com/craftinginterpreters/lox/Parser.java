package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.craftinginterpreters.lox.TokenType.*;

/*
 * Grammar is:
 *
 * program        →  statement* EOF ;
 *
 * declaration    → fun_decl
 *                | var_decl
 *                | statement ;
 *
 * var_decl       → "var" IDENTIFIER ( "=" expression )? ";" ;
 * fun_decl       → "fun" function ;
 *
 * function       → IDENTIFIER "(" parameters? ")" block ;
 *
 * parameters     → IDENTIFIER ( "," IDENTIFIER )* ;
 * arguments      → expression ( "," expression )* ;
 *
 * statement      → expr_stmt
 *                | for_stmt
 *                | if_stmt
 *                | print_stmt
 *                | return_stmt
 *                | while_stmt
 *                | break_stmt
 *                | block ;
 *
 * expr_stmt       → expression ";" ;
 * for_stmt        → "for" "(" ( var_decl | expr_smt | ";" ) expression? ";" expression? ")" statement ;
 * if_stmt         → "if" "(" expression ")" statement ( "else" statement )? ;
 * print_stmt      → "print" expression ";" ;
 * return_stmt     → "return" expression? ";" ;
 * while_stmt      → "while" "(" expression ")" statement ;
 * break_stmt      → "break" ";" ;
 * block           → "{" declaration* "}" ;
 *
 * expression     → assignment ;
 * assignment     → IDENTIFIER "=" assignment
 *                | separator
 *                | logic_or ;
 * separator      → ternary ( "," ternary)* ;
 * ternary        → logic_or ( ( "?" ternary )* ":" ternary )* ;
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality)*
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
 * addition       → multiplication ( ( "-" | "+" ) multiplication )* ;
 * multiplication → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | call ;
 * call           → primary ( "(" arguments? ")" )* ;
 * primary        → "true" | "false" | "nil" | "this"
 *                | NUMBER | STRING
 *                | "(" expression ")"
 *                | IDENTIFIER ;
 */
class Parser {
    private static class ParseError extends RuntimeException {

    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()) {
            statements.add(declaration());
        }

        return statements;
    }

    private Stmt declaration() {
        try {
            if (match(FUN)) return functionDeclaration("function");
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError err) {
            synchronize();

            return null;
        }
    }

    private Stmt.Function functionDeclaration(String kind) {
        Token name = consume(IDENTIFIER, String.format("Expect %s name.", kind));
        consume(LEFT_PAREN, String.format("Expect '(' after %s name", kind));

        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 8) {
                    throw new RuntimeError(peek(), "Cannot have more than 8 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name"));
            } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters");

        // remember, block() expect '{' to already be consumed. So we consume it here.
        consume(LEFT_BRACE, String.format("Expect '{' before %s body.", kind));
        List<Stmt> body = block();

        return new Stmt.Function(name, parameters, body);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");

        return new Stmt.Var(name, initializer);
    }

    private Stmt statement() {
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(BREAK)) return breakStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        return expressionStatement();
    }

    /*
     * The for statement is implemented by de-sugaring it into while.
     *
     * This implementation is not a fancy optimization because mostly
     * the implementation is for learning purpose.
     *
     * Also notice how this way, we don't have to do anything in our
     * Interpreter to have a for loop, because it's basically a
     * while loop that's re-ordered.
     */
    private Stmt forStatement() {
        // region: consuming syntax
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();
        // end-region: consuming syntax

        // We want to build the syntax tree from the deepest part for de-sugaring. For example:
        //
        // for (var i = 0; i < 10; i = i + 1) {
        //    print i;
        // }
        //
        // would become:
        //
        // var i = 0;
        // while (i < 10) {
        //     print i;
        //     i = i + 1;
        // }
        //
        // Notice several things:
        // - All the statements in while (var i = 0, i < 10, print i, i = i + 1) is available in for too.
        // - The order from deepest to shallowest is:
        //
        //   1. block statement ({ print i; i = i + 1; })
        //   2. conditional (i < 10)
        //   3. initialization (var i = 0)
        //
        //   We're going to parse it with the same order.

        // 1. Parse the block statement, with a list of body statement and increment.
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        // 2. Parse the condition.
        //    If there's no condition, we'll put in literal "true" so it's an infinite loop
        if (condition == null) {
            condition = new Expr.Literal(true);
        }

        // 3. Build the while statement tree so we can bundle it with initializer to make sure
        //    initializer is always run before the while statement.
        body = new Stmt.While(condition, body);

        // 4. Bundle the initializer into a block to make sure of two things:
        //
        //    1. The initialized variable is only valid in the for-loop scope
        //    2. The initialized statement is always called before body.
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt breakStatement() {
        consume(SEMICOLON, "Expect ';' after break.");
        return new Stmt.Break();
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while condition.");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt printStatement() {
        return statementParser(Stmt.Print::new);
    }

    private Stmt expressionStatement() {
        return statementParser(Stmt.Expression::new);
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(RIGHT_BRACE, "Expect '}' at the end of block.");
        return statements;
    }

    private Stmt statementParser(Function<Expr, Stmt> generator) {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");

        return generator.apply(expr);
    }

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr expr = separator();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr separator() {
        Expr expr = ternary();

        while (match(COMMA)) {
            Token operator = previous();
            Expr right = ternary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr ternary() {
        Expr expr = or();

        while (match(QUESTION_MARK)) {
            Expr truthy = ternary();

            while (match(COLON)) {
                Expr falsy = ternary();
                expr = new Expr.Ternary(expr, truthy, falsy);

                return expr;
            }
        }

        return expr;
    }

    private Expr or() {
        return logical(this::and, OR);
    }

    private Expr and() {
        return logical(this::equality, AND);
    }

    private Expr logical(Supplier<Expr> parserFunction, TokenType token) {
        Expr expr = parserFunction.get();

        while (match(token)) {
            Token operator = previous();
            Expr right = parserFunction.get();
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    private Expr equality() {
        return binary(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    private Expr comparison() {
        return binary(this::addition, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    private Expr addition() {
        return binary(this::multiplication, MINUS, PLUS);
    }

    private Expr multiplication() {
        return binary(this::unary, STAR, SLASH);
    }

    private Expr binary(Supplier<Expr> parserFunction, TokenType... matchedTokens) {
        Expr expr = parserFunction.get();

        while (match(matchedTokens)) {
            Token operator = previous();
            Expr right = parserFunction.get();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();

            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 8) {
                    error(peek(), "Cannot have more than 8 arguments.");
                }

                // it's ternary here, not expression because:
                // - we don't want the user to be able to declare variables inside function arguments
                // - the separator operator would be confused with parameters if it's expression because
                //   precedence. (Should we change precedence? What about other languages?)
                arguments.add(ternary());
            } while (match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

        return new Expr.Call(callee, paren, arguments);
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression");
            return new Expr.Grouping(expr);
        }

        if (match(STAR, SLASH, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, BANG_EQUAL, EQUAL_EQUAL)) {
            expression();

            throw error(peek(), "Comparator requires expression on the left side.");
        }

        throw error(peek(), "Expect expression.");
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while(!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private boolean match(TokenType... types) {
        for (TokenType type: types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }
}
