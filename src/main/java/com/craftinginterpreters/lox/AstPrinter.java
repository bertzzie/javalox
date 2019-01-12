package com.craftinginterpreters.lox;

// Creates an unambiguous, if ugly, string representation of AST nodes.
class AstPrinter implements Expr.Visitor<String> {

    public static void main(String[] args) {
//        Expr expression = new Expr.Binary(
//                new Expr.Unary(
//                        new Token(TokenType.MINUS, "-", null, 1),
//                        new Expr.Literal(123)
//                ),
//                new Token(TokenType.STAR, "*", null, 1),
//                new Expr.Grouping(new Expr.Literal(45.67))
//        );

        Expr expression = new Expr.Ternary(
            new Expr.Binary(
                new Expr.Literal(100),
                new Token(TokenType.LESS_EQUAL, "<=", null, 1),
                new Expr.Literal(123)
            ),
            new Expr.Literal("true"),
            new Expr.Literal("false")
        );

        System.out.println(new AstPrinter().print(expression));
    }

    String print(Expr expr) {
        return expr.accept(this);
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return null;
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right);
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        return null;
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return parenthesize(
            ": " + parenthesize(
                "?",
                expr.conditional,
                expr.truthy
            ),
            expr.falsy
        );
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return parenthesize("group", expr.expression);
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        if (expr.value == null) return "nil";
        return expr.value.toString();
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return String.format("%s %s %s", expr.left.toString(), expr.operator.lexeme, expr.right.toString());
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return parenthesize(expr.operator.lexeme, expr.right);
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return String.format("var %s", expr.name);
    }

    private String parenthesize(String name, Expr ...exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for(Expr expr: exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }
}
