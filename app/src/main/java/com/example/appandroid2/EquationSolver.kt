package com.example.appandroid2

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Resuelve ecuaciones de una variable "x".
 *  - Lineales y cuadráticas: solución exacta (incluye raíces complejas).
 *  - Otras (x^3, sin(x), 2^x = 8, ...): solución numérica en el intervalo [-1000, 1000].
 */
object EquationSolver {

    fun solve(equation: String): String {
        val parts = equation.split("=")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw IllegalArgumentException("Escribe una ecuación con un solo '=' (ej: 2x+3=7)")
        }
        val left = parts[0]
        val right = parts[1]

        // Validar sintaxis antes de empezar (lanza error con mensaje claro)
        ExpressionParser(left, 0.0).parse()
        ExpressionParser(right, 0.0).parse()

        val f: (Double) -> Double = { v ->
            try {
                ExpressionParser(left, v).parse() - ExpressionParser(right, v).parse()
            } catch (e: Exception) {
                Double.NaN
            }
        }

        solvePolynomial(f)?.let { return it }
        return solveNumeric(f)
    }

    /** Intenta ajustar f(x) = ax² + bx + c; si coincide en varios puntos, resuelve exactamente. */
    private fun solvePolynomial(f: (Double) -> Double): String? {
        val f0 = f(0.0)
        val f1 = f(1.0)
        val fm1 = f(-1.0)
        if (!f0.isFinite() || !f1.isFinite() || !fm1.isFinite()) return null

        val c = f0
        val a = (f1 + fm1) / 2 - f0
        val b = (f1 - fm1) / 2

        val isPoly = listOf(2.0, 3.0, -2.5, 7.0, -6.0).all { t ->
            val expected = a * t * t + b * t + c
            val actual = f(t)
            actual.isFinite() && abs(actual - expected) <= 1e-9 * (1 + abs(expected))
        }
        if (!isPoly) return null

        val eps = 1e-12
        if (abs(a) < eps) {
            return if (abs(b) < eps) {
                if (abs(c) < eps) "Infinitas soluciones (siempre se cumple)" else "Sin solución"
            } else {
                "x = ${formatNumber(-c / b)}"
            }
        }

        val disc = b * b - 4 * a * c
        val scale = max(1.0, max(abs(b * b), abs(4 * a * c)))
        return when {
            abs(disc) < 1e-12 * scale -> "x = ${formatNumber(-b / (2 * a))}\n(raíz doble)"
            disc > 0 -> {
                val r = sqrt(disc)
                val x1 = (-b + r) / (2 * a)
                val x2 = (-b - r) / (2 * a)
                "x₁ = ${formatNumber(x1)}\nx₂ = ${formatNumber(x2)}"
            }
            else -> {
                val re = -b / (2 * a)
                val im = sqrt(-disc) / (2 * abs(a))
                "Sin soluciones reales.\nSoluciones complejas:\nx₁ = ${formatNumber(re)} + ${formatNumber(im)}i\n" +
                        "x₂ = ${formatNumber(re)} − ${formatNumber(im)}i"
            }
        }
    }

    /** Busca cambios de signo en [-1000, 1000] y refina cada raíz por bisección. */
    private fun solveNumeric(f: (Double) -> Double): String {
        val lo = -1000.0
        val step = 0.05
        val steps = 40000
        val roots = mutableListOf<Double>()

        fun addRoot(r: Double) {
            if (roots.none { abs(it - r) < 1e-6 }) roots.add(r)
        }

        var prevX = lo
        var prevY = f(prevX)
        if (prevY == 0.0) addRoot(prevX)

        for (i in 1..steps) {
            val cx = lo + i * step
            val cy = f(cx)
            if (cy == 0.0) {
                addRoot(cx)
            } else if (prevY.isFinite() && cy.isFinite() && prevY * cy < 0) {
                var a = prevX
                var b = cx
                var fa = prevY
                repeat(100) {
                    val m = (a + b) / 2
                    val fm = f(m)
                    if (!fm.isFinite()) return@repeat
                    if (fa * fm <= 0) b = m else { a = m; fa = fm }
                }
                val root = (a + b) / 2
                // Descarta saltos de signo por discontinuidades (ej. 1/x)
                if (abs(f(root)) < 1e-6) addRoot(root)
            }
            prevX = cx
            prevY = cy
        }

        if (roots.isEmpty()) return "No se encontraron soluciones reales en [-1000, 1000]"
        val sorted = roots.sorted()
        return if (sorted.size == 1) {
            "x ≈ ${formatNumber(sorted[0])}"
        } else {
            sorted.mapIndexed { i, r -> "x${subscript(i + 1)} ≈ ${formatNumber(r)}" }.joinToString("\n")
        }
    }

    private fun subscript(n: Int): String =
        n.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")
}