package com.example.appandroid2

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.*

/**
 * Evalúa expresiones matemáticas con:
 *  + - × ÷ (o * /), potencias ^, paréntesis, negativos, decimales,
 *  multiplicación implícita (2x, 3(4+1)), constantes pi y e,
 *  funciones sin, cos, tan, sqrt (√), ln, log, abs.
 *  Los ángulos de sin/cos/tan están en radianes.
 *  Si se pasa [x], la variable "x" toma ese valor (se usa en ecuaciones).
 */
class ExpressionParser(input: String, private val x: Double? = null) {

    private val s = input
        .replace(" ", "")
        .replace('×', '*')
        .replace('÷', '/')
        .replace('−', '-')
        .replace(',', '.')
        .replace("π", "pi")
        .replace("√", "sqrt")
        .lowercase()

    private var pos = 0

    fun parse(): Double {
        if (s.isEmpty()) throw IllegalArgumentException("Expresión vacía")
        pos = 0
        val value = parseExpression()
        if (pos < s.length) throw IllegalArgumentException("Carácter inesperado: '${s[pos]}'")
        return value
    }

    // expresión := término (('+' | '-') término)*
    private fun parseExpression(): Double {
        var v = parseTerm()
        while (pos < s.length) {
            when (s[pos]) {
                '+' -> { pos++; v += parseTerm() }
                '-' -> { pos++; v -= parseTerm() }
                else -> break
            }
        }
        return v
    }

    // término := unario (('*' | '/' | implícita) unario)*
    private fun parseTerm(): Double {
        var v = parseUnary()
        while (pos < s.length) {
            val c = s[pos]
            when {
                c == '*' -> { pos++; v *= parseUnary() }
                c == '/' -> {
                    pos++
                    val d = parseUnary()
                    if (d == 0.0 && x == null) throw ArithmeticException("División entre cero")
                    v /= d
                }
                c == '(' || c.isLetter() -> v *= parseUnary() // multiplicación implícita
                else -> break
            }
        }
        return v
    }

    private fun parseUnary(): Double {
        if (pos < s.length) {
            if (s[pos] == '-') { pos++; return -parseUnary() }
            if (s[pos] == '+') { pos++; return parseUnary() }
        }
        return parsePower()
    }

    // potencia := primario ('^' unario)?   (asociativa a la derecha)
    private fun parsePower(): Double {
        val base = parsePrimary()
        if (pos < s.length && s[pos] == '^') {
            pos++
            return base.pow(parseUnary())
        }
        return base
    }

    private fun parsePrimary(): Double {
        if (pos >= s.length) throw IllegalArgumentException("Expresión incompleta")
        val c = s[pos]
        return when {
            c == '(' -> {
                pos++
                val v = parseExpression()
                closeParen()
                v
            }
            c.isDigit() || c == '.' -> parseNumber()
            c.isLetter() -> parseIdentifier()
            else -> throw IllegalArgumentException("Carácter inesperado: '$c'")
        }
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        return s.substring(start, pos).toDoubleOrNull()
            ?: throw IllegalArgumentException("Número inválido")
    }

    private fun parseIdentifier(): Double {
        val start = pos
        while (pos < s.length && s[pos].isLetter()) pos++
        val name = s.substring(start, pos)
        return when (name) {
            "x" -> x ?: throw IllegalArgumentException("Usa 'x' solo en el modo Ecuaciones")
            "pi" -> PI
            "e" -> E
            "sin", "cos", "tan", "sqrt", "ln", "log", "abs" -> {
                if (pos >= s.length || s[pos] != '(') {
                    throw IllegalArgumentException("Falta '(' después de $name")
                }
                pos++
                val arg = parseExpression()
                closeParen()
                applyFunction(name, arg)
            }
            else -> throw IllegalArgumentException("Función desconocida: $name")
        }
    }

    private fun applyFunction(name: String, a: Double): Double = when (name) {
        "sin" -> sin(a)
        "cos" -> cos(a)
        "tan" -> tan(a)
        "sqrt" -> if (a < 0 && x == null) throw ArithmeticException("Raíz de número negativo") else sqrt(a)
        "ln" -> if (a <= 0 && x == null) throw ArithmeticException("ln solo existe para valores > 0") else ln(a)
        "log" -> if (a <= 0 && x == null) throw ArithmeticException("log solo existe para valores > 0") else log10(a)
        "abs" -> abs(a)
        else -> throw IllegalArgumentException("Función desconocida: $name")
    }

    // Si falta cerrar al final de la expresión, se cierra automáticamente: "sin(30" -> "sin(30)"
    private fun closeParen() {
        if (pos < s.length) {
            if (s[pos] == ')') pos++ else throw IllegalArgumentException("Falta ')'")
        }
    }
}

/** Formatea un número a máximo 10 cifras significativas, sin ceros sobrantes. */
fun formatNumber(value: Double): String {
    if (value.isNaN()) return "No definido"
    if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
    if (abs(value) < 1e-12) return "0"
    return BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString()
}