package com.example.automathtapper

object MathSolver {
    // Very simple solver for expressions like "4 + 1 =", "7-2=", "3×4=", "10 ÷ 5 ="
    fun solveSimple(exprRaw: String): Int? {
        val expr = exprRaw
            .replace(" ", "")
            .replace("=", "")
            .replace("×", "*")
            .replace("x", "*")
            .replace("X", "*")
            .replace("÷", "/")
            .replace("—", "-")
            .replace("−", "-")

        val ops = listOf('+','-','*','/')
        val opIndex = expr.indexOfFirst { it in ops }
        if (opIndex <= 0) return null

        val a = expr.substring(0, opIndex).toIntOrNull() ?: return null
        val b = expr.substring(opIndex+1).toIntOrNull() ?: return null
        return when (expr[opIndex]) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (b != 0) a / b else null
            else -> null
        }
    }
}