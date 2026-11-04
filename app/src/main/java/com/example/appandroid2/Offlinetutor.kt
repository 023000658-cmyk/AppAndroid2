package com.example.appandroid2

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Tutor de matemáticas que funciona SIN internet.
 * Entiende:
 *  - Operaciones: (3+4)*2^2-5  → con el orden de operaciones paso a paso
 *  - Ecuaciones: 2x+3=7, x^2-5x+6=0 → con los pasos
 *  - Derivada / integral de un polinomio, factorizar, expandir
 *  - Porcentajes: 20% de 150
 *  - Explicaciones de temas básicos (fórmula general, Pitágoras, fracciones…)
 */
object OfflineTutor {

    fun answer(question: String): String {
        val raw = question.trim()
        if (raw.isEmpty()) return HELP
        return try {
            route(normalize(raw))
        } catch (e: Exception) {
            "No pude resolver eso: ${e.message ?: "la expresión no es válida"}.\n\n$SHORT_HELP"
        }
    }

    // ───────────────────────── Enrutador ─────────────────────────

    private fun route(n: String): String {
        findExpr(n, "deriv\\w*")?.let { return derivative(it) }
        findExpr(n, "integra\\w*")?.let { return integral(it) }
        findExpr(n, "factori\\w*")?.let { return factorize(it) }
        findExpr(n, "(?:expand\\w*|desarroll\\w*|simplific\\w*)")?.let { return expand(it) }

        PERCENT.find(n)?.let { m ->
            val a = m.groupValues[1].replace(',', '.').toDouble()
            val b = m.groupValues[2].replace(',', '.').toDouble()
            return percent(a, b)
        }

        val cleaned = clean(n)
        if (looksLikeMath(cleaned)) {
            return when {
                '=' in cleaned -> solveEquation(cleaned)
                'x' in cleaned -> expand(cleaned)
                else -> arithmetic(cleaned)
            }
        }

        faq(n)?.let { return it }
        return HELP
    }

    private val PERCENT = Regex("(\\d+(?:[.,]\\d+)?)\\s*%\\s*(?:de|of)?\\s*(\\d+(?:[.,]\\d+)?)")

    private val FILLER = Regex(
        "(resuelve|resolver|resuelva|calcula|calcular|cuanto es|cuanto vale|ayudame con|ayudame|" +
                "ayuda con|por favor|paso a paso|la ecuacion|ecuacion|el resultado de)"
    )

    /** Minúsculas y sin acentos, para reconocer palabras aunque se escriban distinto. */
    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")

    private fun clean(s: String): String =
        s.replace(FILLER, " ").replace("?", " ").replace("¿", " ").replace("!", " ").trim()

    private fun looksLikeMath(s: String): Boolean {
        if (s.isBlank()) return false
        val stripped = s.replace(Regex("sin|cos|tan|sqrt|ln|log|abs|pi"), "")
        val allowed = "x+-*/^()=.,×÷−π "
        return stripped.all { it.isDigit() || it in allowed } && s.any { it.isDigit() || it == 'x' }
    }

    /** Busca "<palabra clave> [de] <expresión>" y devuelve la expresión si parece matemática. */
    private fun findExpr(n: String, keyword: String): String? {
        val m = Regex("$keyword\\s*(?:de|d)?\\s*(.*)").find(n) ?: return null
        val expr = clean(m.groupValues[1])
            .replace(Regex("^(f\\(x\\)|y|f)\\s*=\\s*"), "")
            .replace(Regex("\\s*dx$"), "")
            .trim()
        return if (looksLikeMath(expr) && '=' !in expr) expr else null
    }

    // ───────────────────────── Polinomios ─────────────────────────

    /**
     * Si la expresión (en x) es un polinomio de grado ≤ 6, devuelve sus coeficientes [a0, a1, …, an].
     * Se obtienen evaluando la expresión en 7 puntos y resolviendo el sistema; después se comprueba en otros puntos.
     */
    private fun polyCoeffs(expr: String): DoubleArray? {
        val n = 7
        val xs = DoubleArray(n) { (it - 3).toDouble() }
        val ys = DoubleArray(n)
        for (i in 0 until n) {
            ys[i] = try {
                ExpressionParser(expr, xs[i]).parse()
            } catch (e: Exception) {
                return null
            }
            if (!ys[i].isFinite()) return null
        }

        val a = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) xs[i].pow(j) else ys[i] } }
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[piv][col])) piv = r
            if (abs(a[piv][col]) < 1e-12) return null
            val tmp = a[col]
            a[col] = a[piv]
            a[piv] = tmp
            for (r in 0 until n) {
                if (r == col) continue
                val f = a[r][col] / a[col][col]
                for (k in col..n) a[r][k] -= f * a[col][k]
            }
        }
        val c = DoubleArray(n) { a[it][n] / a[it][it] }

        // Comprobación en otros puntos: si no coincide, no es un polinomio
        for (x in listOf(-5.0, -1.5, 0.5, 4.5, 6.0)) {
            val actual = try {
                ExpressionParser(expr, x).parse()
            } catch (e: Exception) {
                return null
            }
            val expected = evalPoly(c, x)
            if (!actual.isFinite() || abs(actual - expected) > 1e-6 * (1 + abs(expected))) return null
        }

        for (i in c.indices) {
            val r = c[i].roundToInt().toDouble()
            c[i] = if (abs(c[i] - r) < 1e-7) r else c[i]
            if (abs(c[i]) < 1e-9) c[i] = 0.0
        }
        var d = n - 1
        while (d > 0 && c[d] == 0.0) d--
        return c.copyOf(d + 1)
    }

    private fun evalPoly(c: DoubleArray, x: Double): Double {
        var r = 0.0
        for (k in c.indices.reversed()) r = r * x + c[k]
        return r
    }

    private fun formatPoly(c: DoubleArray): String {
        val sb = StringBuilder()
        for (k in c.indices.reversed()) {
            val v = c[k]
            if (abs(v) < 1e-9) continue
            val neg = v < 0
            val av = abs(v)
            if (sb.isEmpty()) {
                if (neg) sb.append("-")
            } else {
                sb.append(if (neg) " - " else " + ")
            }
            if (!(k > 0 && abs(av - 1) < 1e-9)) sb.append(formatNumber(av))
            when (k) {
                0 -> {}
                1 -> sb.append("x")
                else -> sb.append("x^$k")
            }
        }
        return if (sb.isEmpty()) "0" else sb.toString()
    }

    private fun monomial(coef: Double, exp: Int): String =
        formatPoly(DoubleArray(exp + 1) { if (it == exp) coef else 0.0 })

    private const val NOT_POLY = "Solo sé trabajar con polinomios en x (por ejemplo x^3+2x-5 o (x+1)^2)."

    private fun expand(expr: String): String {
        val c = polyCoeffs(expr) ?: return NOT_POLY
        return "📝 Forma simplificada de $expr:\n\n   ${formatPoly(c)}\n\n" +
                "Grado del polinomio: ${c.size - 1}"
    }

    private fun derivative(expr: String): String {
        val c = polyCoeffs(expr) ?: return NOT_POLY
        val d = DoubleArray(max(1, c.size - 1)) { k -> if (c.size == 1) 0.0 else (k + 1) * c[k + 1] }
        val sb = StringBuilder()
        sb.append("📈 Derivada de f(x) = ${formatPoly(c)}\n\n")
        sb.append("Regla: la derivada de a·x^n es n·a·x^(n−1), y la de un número solo es 0.\n\n")
        for (k in c.indices.reversed()) {
            if (c[k] == 0.0) continue
            if (k == 0) {
                sb.append("• ${formatNumber(c[0])}  →  0\n")
            } else {
                sb.append("• ${monomial(c[k], k)}  →  ${monomial(k * c[k], k - 1)}\n")
            }
        }
        sb.append("\n✅ f'(x) = ${formatPoly(d)}")
        return sb.toString()
    }

    private fun integral(expr: String): String {
        val c = polyCoeffs(expr) ?: return NOT_POLY
        val terms = mutableListOf<Pair<Boolean, String>>() // (es negativo, texto)
        for (k in c.indices.reversed()) {
            val a = c[k]
            if (a == 0.0) continue
            val m = k + 1
            val q = a / m
            val body = if (abs(q - q.roundToInt()) < 1e-9) {
                formatPoly(DoubleArray(m + 1) { if (it == m) abs(q) else 0.0 })
            } else {
                val num = if (abs(abs(a) - 1) < 1e-9) "" else formatNumber(abs(a))
                "${num}x^$m/$m"
            }
            terms.add((a < 0) to body)
        }
        val sb = StringBuilder()
        terms.forEachIndexed { i, (neg, body) ->
            if (i == 0) {
                if (neg) sb.append("-")
            } else {
                sb.append(if (neg) " - " else " + ")
            }
            sb.append(body)
        }
        val result = if (sb.isEmpty()) "C" else "$sb + C"
        return "∫ Integral de f(x) = ${formatPoly(c)}\n\n" +
                "Regla: la integral de a·x^n es a·x^(n+1)/(n+1). Al final se suma la constante C.\n\n" +
                "✅ $result"
    }

    private fun factorize(expr: String): String {
        val c = polyCoeffs(expr) ?: return NOT_POLY
        if (c.size < 2) return "Eso es solo un número, no hay nada que factorizar."

        var p = c.copyOf()
        val roots = mutableListOf<Int>()
        while (p.size > 1) {
            var found = false
            for (r in -50..50) {
                if (abs(evalPoly(p, r.toDouble())) < 1e-7) {
                    p = divideByRoot(p, r.toDouble())
                    roots.add(r)
                    found = true
                    break
                }
            }
            if (!found) break
        }
        if (roots.isEmpty()) {
            return "🔎 ${formatPoly(c)}\n\nNo encontré raíces enteras, así que no se factoriza con números enteros."
        }

        val grouped = roots.groupingBy { it }.eachCount().toSortedMap()
        val factors = grouped.entries.joinToString("") { (r, times) ->
            val base = when {
                r == 0 -> "x"
                r > 0 -> "(x - $r)"
                else -> "(x + ${-r})"
            }
            if (times > 1) "$base^$times" else base
        }
        val prefix: String
        val rest: String
        if (p.size == 1) {
            prefix = if (abs(p[0] - 1) < 1e-9) "" else formatNumber(p[0])
            rest = ""
        } else {
            prefix = ""
            rest = "(${formatPoly(p)})"
        }
        val tip = "Cada factor (x − r) viene de una raíz r: si x = r, el polinomio vale 0."
        return "🔎 Factorización de ${formatPoly(c)}\n\n✅ $prefix$factors$rest\n\n$tip" +
                if (p.size > 1) "\n\nEl último factor ya no se divide con números enteros." else ""
    }

    /** División sintética: divide el polinomio entre (x − r). */
    private fun divideByRoot(p: DoubleArray, r: Double): DoubleArray {
        val n = p.size - 1
        val q = DoubleArray(n)
        q[n - 1] = p[n]
        for (k in n - 1 downTo 1) q[k - 1] = p[k] + r * q[k]
        return q
    }

    // ───────────────────────── Ecuaciones ─────────────────────────

    private fun solveEquation(eq: String): String {
        val parts = eq.split("=")
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw IllegalArgumentException("escribe una ecuación con un solo signo =, por ejemplo 2x+3=7")
        }
        val left = parts[0].trim()
        val right = parts[1].trim()
        ExpressionParser(left, 0.0).parse()
        ExpressionParser(right, 0.0).parse()

        val header = "📐 Ecuación: $left = $right\n\n"
        val c = polyCoeffs("($left)-($right)")
            ?: return header + "No es un polinomio, así que busco las soluciones numéricamente " +
                    "(entre -1000 y 1000):\n\n${EquationSolver.solve(eq)}"

        return header + when (c.size - 1) {
            0 -> if (c[0] == 0.0) {
                "Los dos lados son iguales para cualquier x: tiene infinitas soluciones."
            } else {
                "Al pasar todo a un lado queda ${formatNumber(c[0])} = 0, que es imposible: no tiene solución."
            }
            1 -> linear(c, left, right)
            2 -> quadratic(c)
            else -> "Es una ecuación de grado ${c.size - 1}. No hay una fórmula sencilla, " +
                    "así que busco las soluciones numéricamente:\n\n${EquationSolver.solve(eq)}"
        }
    }

    private fun par(v: Double) = if (v < 0) "(${formatNumber(v)})" else formatNumber(v)

    private fun linear(c: DoubleArray, left: String, right: String): String {
        val a = c[1]
        val b = c[0]
        val x = -b / a
        val ax = when {
            abs(a - 1) < 1e-9 -> "x"
            abs(a + 1) < 1e-9 -> "-x"
            else -> "${formatNumber(a)}x"
        }
        val sb = StringBuilder()
        sb.append("Es una ecuación lineal (de primer grado).\n\n")
        sb.append("1) Pasamos todo a un lado, igualado a 0:\n   ${formatPoly(c)} = 0\n\n")
        sb.append("2) Dejamos la x sola, pasando el número al otro lado:\n   $ax = ${formatNumber(-b)}\n\n")
        if (abs(abs(a) - 1) > 1e-9) {
            sb.append("3) Dividimos entre ${par(a)}:\n   x = ${formatNumber(-b)} ÷ ${par(a)} = ${formatNumber(x)}\n\n")
        } else if (a < 0) {
            sb.append("3) Cambiamos el signo de los dos lados:\n   x = ${formatNumber(x)}\n\n")
        }
        sb.append("✅ x = ${formatNumber(x)}")
        val l = runCatching { ExpressionParser(left, x).parse() }.getOrNull()
        val r = runCatching { ExpressionParser(right, x).parse() }.getOrNull()
        if (l != null && r != null && abs(l - r) < 1e-6 * (1 + abs(l))) {
            sb.append("\n\nComprobación: con x = ${formatNumber(x)}, el lado izquierdo vale ${formatNumber(l)} " +
                    "y el derecho ${formatNumber(r)}. ¡Coinciden!")
        }
        return sb.toString()
    }

    private fun quadratic(c: DoubleArray): String {
        val a = c[2]
        val b = c[1]
        val cc = c[0]
        val d = b * b - 4 * a * cc
        val scale = max(1.0, max(abs(b * b), abs(4 * a * cc)))
        val sb = StringBuilder()
        sb.append("Es una ecuación cuadrática (de segundo grado), de la forma ax² + bx + c = 0.\n\n")
        sb.append("1) Pasamos todo a un lado e identificamos los números:\n")
        sb.append("   ${formatPoly(c)} = 0\n   a = ${formatNumber(a)},  b = ${formatNumber(b)},  c = ${formatNumber(cc)}\n\n")
        sb.append("2) Calculamos el discriminante D = b² − 4ac:\n")
        sb.append("   D = ${par(b)}² − 4·${par(a)}·${par(cc)} = ${formatNumber(d)}\n\n")

        when {
            abs(d) < 1e-12 * scale -> {
                val x = -b / (2 * a)
                sb.append("3) Como D = 0, hay una sola solución (doble):\n")
                sb.append("   x = −b / (2a) = ${par(-b)} / ${par(2 * a)} = ${formatNumber(x)}\n\n")
                sb.append("✅ x = ${formatNumber(x)}")
            }
            d > 0 -> {
                val r = sqrt(d)
                val x1 = (-b + r) / (2 * a)
                val x2 = (-b - r) / (2 * a)
                sb.append("3) Como D > 0, hay dos soluciones reales. Usamos la fórmula general:\n")
                sb.append("   x = (−b ± √D) / (2a)\n")
                sb.append("   x = (${par(-b)} ± √${formatNumber(d)}) / ${par(2 * a)}\n")
                sb.append("   x₁ = ${formatNumber(x1)}\n   x₂ = ${formatNumber(x2)}\n\n")
                sb.append("✅ x₁ = ${formatNumber(x1)},  x₂ = ${formatNumber(x2)}")
            }
            else -> {
                val re = -b / (2 * a)
                val im = sqrt(-d) / (2 * abs(a))
                sb.append("3) Como D < 0, no hay soluciones reales (la raíz de un número negativo no es real).\n")
                sb.append("   Las soluciones son complejas:\n\n")
                sb.append("✅ x = ${formatNumber(re)} ± ${formatNumber(im)}i")
            }
        }
        return sb.toString()
    }

    // ───────────────────────── Operaciones y porcentajes ─────────────────────────

    private fun arithmetic(expr: String): String {
        val value = ExpressionParser(expr).parse()
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("el resultado no está definido")
        val steps = arithmeticSteps(expr, value)
        return buildString {
            append("🧮 Operación: ${expr.trim()}\n\n")
            if (steps != null) {
                append("Orden de operaciones: paréntesis, potencias, × y ÷, y al final + y −.\n\n")
                append(steps.joinToString("\n"))
                append("\n\n")
            }
            append("✅ Resultado: ${formatNumber(value)}")
            if (Regex("sin|cos|tan").containsMatchIn(expr)) append("\n(sin, cos y tan usan radianes)")
        }
    }

    private fun percent(a: Double, b: Double): String {
        val r = a * b / 100
        return "📊 ${formatNumber(a)}% de ${formatNumber(b)}\n\n" +
                "1) Un porcentaje es una fracción de 100: ${formatNumber(a)}% = ${formatNumber(a)} ÷ 100 = ${formatNumber(a / 100)}\n" +
                "2) Multiplicamos por la cantidad: ${formatNumber(a / 100)} × ${formatNumber(b)} = ${formatNumber(r)}\n\n" +
                "✅ ${formatNumber(r)}"
    }

    /** Muestra la operación reduciéndose un paso a la vez. Devuelve null si no se puede explicar así. */
    private fun arithmeticSteps(expr: String, expected: Double): List<String>? {
        val start = tokenize(expr) ?: return null
        var tokens: List<String> = start
        val lines = mutableListOf<String>()
        var guard = 0
        while (tokens.size > 1 && guard++ < 40) {
            val (next, desc) = stepOnce(tokens) ?: return null
            tokens = next
            lines.add("= ${show(tokens)}      ← $desc")
        }
        if (tokens.size != 1) return null
        val last = tokens[0].toDoubleOrNull() ?: return null
        if (abs(last - expected) > 1e-6 * (1 + abs(expected))) return null
        return if (lines.size >= 2) listOf(show(start)) + lines else null
    }

    private fun show(tokens: List<String>): String =
        tokens.joinToString(" ") { it.replace("*", "×").replace("/", "÷").replace("-", "−") }

    private fun tokenize(s: String): List<String>? {
        val src = s.replace(" ", "").replace('×', '*').replace('÷', '/').replace('−', '-').replace(',', '.')
        val raw = mutableListOf<String>()
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            when {
                ch.isDigit() || ch == '.' -> {
                    var j = i
                    while (j < src.length && (src[j].isDigit() || src[j] == '.')) j++
                    raw.add(src.substring(i, j))
                    i = j
                }
                ch in "+-*/^()" -> {
                    raw.add(ch.toString())
                    i++
                }
                else -> return null
            }
        }
        // Signos negativos pegados al número (−2 + 3). Con potencia, −2^2 se lee como −(2^2).
        val ops = listOf("+", "-", "*", "/", "^", "(")
        val out = mutableListOf<String>()
        var k = 0
        while (k < raw.size) {
            val t = raw[k]
            val prev = out.lastOrNull()
            val unary = t == "-" && (prev == null || prev in ops)
            if (unary && k + 1 < raw.size) {
                val nxt = raw[k + 1]
                if (nxt[0].isDigit() || nxt[0] == '.') {
                    val afterPower = k + 2 < raw.size && raw[k + 2] == "^" && prev != "^"
                    if (afterPower) {
                        out.add("-1")
                        out.add("*")
                        out.add(nxt)
                    } else {
                        out.add("-$nxt")
                    }
                    k += 2
                    continue
                } else if (nxt == "(") {
                    out.add("-1")
                    out.add("*")
                    k++
                    continue
                }
            }
            out.add(t)
            k++
        }
        return out
    }

    private fun stepOnce(t: List<String>): Pair<List<String>, String>? {
        val close = t.indexOf(")")
        if (close >= 0) {
            val open = t.subList(0, close).lastIndexOf("(")
            if (open < 0) return null
            val inner = t.subList(open + 1, close)
            if (inner.size == 1) {
                return (t.subList(0, open) + inner + t.subList(close + 1, t.size)) to "quitamos los paréntesis"
            }
            val (newInner, desc) = reduceFlat(inner) ?: return null
            return (t.subList(0, open + 1) + newInner + t.subList(close, t.size)) to desc
        }
        return reduceFlat(t)
    }

    private fun reduceFlat(t: List<String>): Pair<List<String>, String>? {
        if (t.size < 3) return null
        var idx = t.lastIndexOf("^")
        if (idx < 0) idx = t.indexOfFirst { it == "*" || it == "/" }
        if (idx < 0) idx = t.indexOfFirst { it == "+" || it == "-" }
        if (idx <= 0 || idx >= t.size - 1) return null
        val a = t[idx - 1].toDoubleOrNull() ?: return null
        val b = t[idx + 1].toDoubleOrNull() ?: return null
        val op = t[idx]
        val r = when (op) {
            "^" -> a.pow(b)
            "*" -> a * b
            "/" -> a / b
            "+" -> a + b
            else -> a - b
        }
        if (!r.isFinite()) return null
        val symbol = when (op) {
            "*" -> "×"
            "/" -> "÷"
            "-" -> "−"
            else -> op
        }
        val desc = "${par(a)} $symbol ${par(b)} = ${formatNumber(r)}"
        return (t.subList(0, idx - 1) + formatNumber(r) + t.subList(idx + 2, t.size)) to desc
    }

    // ───────────────────────── Temas ─────────────────────────

    private class Topic(val keywords: List<String>, val text: String)

    private fun faq(n: String): String? =
        TOPICS.firstOrNull { t -> t.keywords.any { it in n } }?.text

    private val TOPICS = listOf(
        Topic(
            listOf("jerarquia", "orden de las operaciones", "orden de operaciones", "pemdas"),
            "📚 Orden de las operaciones\n\n" +
                    "Cuando hay varias operaciones juntas, se resuelven en este orden:\n" +
                    "1) Paréntesis\n2) Potencias y raíces\n3) Multiplicaciones y divisiones (de izquierda a derecha)\n" +
                    "4) Sumas y restas (de izquierda a derecha)\n\n" +
                    "Ejemplo: 3 + 4 × 2 = 3 + 8 = 11 (no 14).\nPrueba escribiendo (3+4)*2^2-5 y te muestro los pasos."
        ),
        Topic(
            listOf("discriminante"),
            "📚 Discriminante\n\n" +
                    "En ax² + bx + c = 0 se calcula D = b² − 4ac.\n" +
                    "• D > 0: dos soluciones reales distintas\n• D = 0: una solución doble\n" +
                    "• D < 0: no hay soluciones reales (son complejas)\n\n" +
                    "Prueba con x^2-5x+6=0."
        ),
        Topic(
            listOf("formula general", "ecuacion cuadratica", "segundo grado", "chicharronera", "cuadratica"),
            "📚 Fórmula general (ecuación cuadrática)\n\n" +
                    "Para ax² + bx + c = 0:\n   x = (−b ± √(b² − 4ac)) / (2a)\n\n" +
                    "Pasos: 1) identifica a, b y c; 2) calcula b² − 4ac; 3) sustituye en la fórmula; " +
                    "4) el ± da dos soluciones.\n\nEscribe una ecuación como x^2-5x+6=0 y la resuelvo contigo."
        ),
        Topic(
            listOf("derivada", "derivar"),
            "📚 Derivada\n\n" +
                    "Mide qué tan rápido cambia una función; en una gráfica es la pendiente de la recta tangente.\n" +
                    "Reglas básicas:\n• La derivada de un número es 0\n• De x^n es n·x^(n−1)\n" +
                    "• De a·x^n es a·n·x^(n−1)\n• La derivada de una suma es la suma de las derivadas\n\n" +
                    "Pídeme, por ejemplo: derivada de x^3+2x"
        ),
        Topic(
            listOf("integral", "integrar", "primitiva"),
            "📚 Integral\n\n" +
                    "Es la operación contraria a la derivada: encuentra la función que, al derivarla, da la original.\n" +
                    "Regla: la integral de a·x^n es a·x^(n+1)/(n+1), y al final se suma una constante C.\n\n" +
                    "Pídeme, por ejemplo: integral de 3x^2+2x"
        ),
        Topic(
            listOf("pitagoras", "hipotenusa", "triangulo rectangulo"),
            "📚 Teorema de Pitágoras\n\n" +
                    "En un triángulo rectángulo: a² + b² = c², donde c es la hipotenusa (el lado más largo) " +
                    "y a y b son los catetos.\n\nEjemplo: catetos 3 y 4 → c = √(9 + 16) = √25 = 5."
        ),
        Topic(
            listOf("porcentaje", "por ciento"),
            "📚 Porcentajes\n\n" +
                    "Un porcentaje es una parte de 100. Para sacar el p% de una cantidad: (p ÷ 100) × cantidad.\n" +
                    "Ejemplo: 20% de 150 = 0.20 × 150 = 30.\n\nPrueba escribiendo: 20% de 150"
        ),
        Topic(
            listOf("fraccion", "fracciones"),
            "📚 Fracciones\n\n" +
                    "• Sumar o restar: se necesita el mismo denominador. 1/2 + 1/3 = 3/6 + 2/6 = 5/6\n" +
                    "• Multiplicar: arriba por arriba y abajo por abajo. 2/3 × 4/5 = 8/15\n" +
                    "• Dividir: se multiplica por la fracción volteada. 2/3 ÷ 4/5 = 2/3 × 5/4 = 10/12 = 5/6\n" +
                    "Siempre simplifica al final."
        ),
        Topic(
            listOf("area", "perimetro"),
            "📚 Áreas y perímetros\n\n" +
                    "• Rectángulo: área = base × altura; perímetro = 2(base + altura)\n" +
                    "• Triángulo: área = base × altura ÷ 2\n" +
                    "• Círculo: área = π·r²; perímetro (circunferencia) = 2·π·r\n" +
                    "• Cuadrado: área = lado²; perímetro = 4·lado"
        ),
        Topic(
            listOf("ecuacion lineal", "primer grado", "despejar"),
            "📚 Ecuaciones lineales\n\n" +
                    "La meta es dejar la x sola. Lo que suma pasa restando al otro lado, lo que multiplica pasa dividiendo " +
                    "(y al revés). Lo que haces de un lado lo haces del otro.\n\n" +
                    "Ejemplo: 2x + 3 = 7 → 2x = 4 → x = 2.\nEscribe una ecuación y te la resuelvo paso a paso."
        ),
        Topic(
            listOf("factoriz"),
            "📚 Factorizar\n\n" +
                    "Es escribir un polinomio como una multiplicación. Para x² + bx + c busca dos números que " +
                    "sumados den b y multiplicados den c.\n" +
                    "Ejemplo: x² − 5x + 6 = (x − 2)(x − 3), porque −2 + −3 = −5 y (−2)(−3) = 6.\n\n" +
                    "Pídeme: factoriza x^2-5x+6"
        ),
        Topic(
            listOf("potencia", "exponente"),
            "📚 Potencias\n\n" +
                    "• a^m × a^n = a^(m+n)\n• a^m ÷ a^n = a^(m−n)\n• (a^m)^n = a^(m·n)\n" +
                    "• a^0 = 1\n• a^(−n) = 1 / a^n\n• a^(1/2) = √a"
        ),
        Topic(
            listOf("logaritmo", "logaritmos"),
            "📚 Logaritmos\n\n" +
                    "log_b(x) = y significa que b^y = x. Por ejemplo log₁₀(1000) = 3 porque 10³ = 1000.\n" +
                    "• log(a·b) = log a + log b\n• log(a/b) = log a − log b\n• log(a^n) = n·log a\n" +
                    "En la calculadora, ln es el logaritmo natural (base e) y log el de base 10."
        ),
        Topic(
            listOf("seno", "coseno", "tangente", "trigonometr"),
            "📚 Trigonometría básica\n\n" +
                    "En un triángulo rectángulo, para un ángulo:\n• seno = cateto opuesto ÷ hipotenusa\n" +
                    "• coseno = cateto adyacente ÷ hipotenusa\n• tangente = cateto opuesto ÷ cateto adyacente\n\n" +
                    "Ojo: en la calculadora sin, cos y tan usan radianes (180° = π radianes)."
        ),
        Topic(
            listOf("promedio", "media aritmetica"),
            "📚 Promedio (media)\n\nSuma todos los valores y divide entre cuántos son.\n" +
                    "Ejemplo: 8, 9 y 10 → (8+9+10) ÷ 3 = 9.\nEscribe (8+9+10)/3 y te muestro los pasos."
        ),
        Topic(
            listOf("regla de tres"),
            "📚 Regla de tres simple\n\n" +
                    "Si a corresponde a b, ¿a cuánto corresponde c? Se calcula (b × c) ÷ a.\n" +
                    "Ejemplo: 3 kg cuestan $60, ¿cuánto cuestan 5 kg? → (60 × 5) ÷ 3 = $100."
        ),
        Topic(
            listOf("raiz", "raices"),
            "📚 Raíces\n\n" +
                    "La raíz cuadrada de un número es el que, multiplicado por sí mismo, da ese número: √25 = 5 porque 5×5 = 25.\n" +
                    "En la calculadora usa la tecla √. No existe en los reales para números negativos."
        )
    )

    // ───────────────────────── Ayuda ─────────────────────────

    private const val SHORT_HELP =
        "Prueba con: 2x+3=7, x^2-5x+6=0, (3+4)*2^2-5, derivada de x^3+2x o 20% de 150."

    private const val HELP =
        "Funciono sin internet. Puedo ayudarte con:\n\n" +
                "• Operaciones paso a paso: (3+4)*2^2-5\n" +
                "• Ecuaciones: 2x+3=7 o x^2-5x+6=0\n" +
                "• Derivadas e integrales de polinomios: derivada de x^3+2x\n" +
                "• Factorizar o simplificar: factoriza x^2-5x+6\n" +
                "• Porcentajes: 20% de 150\n" +
                "• Explicaciones: fórmula general, Pitágoras, fracciones, áreas, logaritmos, trigonometría…\n\n" +
                "Escribe tu pregunta o toca un ejemplo."
}