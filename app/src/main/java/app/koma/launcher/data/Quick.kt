package app.koma.launcher.data

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs

/** An instant answer shown in search: a sum, a unit conversion, a web address or a phone number. */
class QuickAnswer(val kind: String, val title: String, val sub: String, val value: String)

object Quick {
    fun answer(raw: String): QuickAnswer? {
        val q = raw.trim()
        if (q.isEmpty()) return null
        phone(q)?.let { return it }
        url(q)?.let { return it }
        unit(q)?.let { return it }
        return calc(q)
    }

    // ---- phone and web ----------------------------------------------------------------------

    private fun phone(q: String): QuickAnswer? {
        if (!Regex("^\\+?\\d[\\d\\s-]*$").matches(q)) return null
        if (q.count { it.isDigit() } < 7) return null
        val n = q.filter { it.isDigit() || it == '+' }
        return QuickAnswer("phone", "Call $q", "PHONE NUMBER", n)
    }

    private fun url(q: String): QuickAnswer? {
        if (q.contains(' ')) return null
        val ok = Regex("^(https?://\\S+|[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}(/\\S*)?)$").matches(q)
        if (!ok) return null
        val full = if (q.startsWith("http", ignoreCase = true)) q else "https://$q"
        return QuickAnswer("url", "Open " + q.removePrefix("https://").removePrefix("http://"), "WEB ADDRESS", full)
    }

    // ---- units ------------------------------------------------------------------------------

    private val length = mapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "mi" to 1609.344, "ft" to 0.3048, "in" to 0.0254, "yd" to 0.9144)
    private val mass = mapOf("kg" to 1.0, "g" to 0.001, "lb" to 0.45359237, "oz" to 0.028349523, "mg" to 0.000001)
    private val volume = mapOf("l" to 1.0, "ml" to 0.001, "gal" to 3.785411784, "cup" to 0.2365882365, "floz" to 0.0295735296)
    private val temps = setOf("c", "f", "k")

    private fun unit(q: String): QuickAnswer? {
        val m = Regex("^(-?[\\d.]+)\\s*([A-Za-z]+)\\s+(?:to|in|as|->)\\s+([A-Za-z]+)$").matchEntire(q) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val a = m.groupValues[2].lowercase()
        val b = m.groupValues[3].lowercase()
        val out: Double = when {
            a in temps && b in temps -> {
                val c = when (a) { "c" -> v; "f" -> (v - 32) * 5 / 9; else -> v - 273.15 }
                when (b) { "c" -> c; "f" -> c * 9 / 5 + 32; else -> c + 273.15 }
            }
            else -> {
                val table = listOf(length, mass, volume).firstOrNull { a in it && b in it } ?: return null
                v * table.getValue(a) / table.getValue(b)
            }
        }
        val text = fmt(out)
        return QuickAnswer("unit", "$text $b", "${fmt(v)} ${a.uppercase()} = TAP TO COPY", text)
    }

    // ---- sums -------------------------------------------------------------------------------

    private val mathNames = listOf("asin", "acos", "atan", "sinh", "cosh", "tanh", "sqrt", "cbrt", "sin", "cos", "tan", "log", "exp", "abs", "ln")

    /**
     * Anything the calculator app can work out: sums, powers, roots, factorials, percentages, trigonometry and
     * logarithms ("sqrt(144) + 2^10", "sin 30", "5!", "cos(60) x 8"). Angles are in degrees unless the word "rad" is added.
     */
    private fun calc(q: String): QuickAnswer? {
        var s = q.lowercase().replace('×', '*').replace('÷', '/').replace(',', '.').replace('−', '-')
        val rad = Regex("\\brad\\b").containsMatchIn(s)
        s = s.replace(Regex("\\b(rad|deg)\\b"), "")
        s = s.replace("²", "^2").replace("³", "^3").replace(Regex("\\bpi\\b"), "π")
        s = s.replace(Regex("(?<=[\\d)\\s])x(?=[\\d(\\s.])"), "*")
        if (!Regex("^[0-9a-z.+\\-*/^()%!√∛π\\s]+$").matches(s)) return null
        if (!s.any { it.isDigit() || it == 'π' }) return null
        val hasFn = s.contains('√') || s.contains('∛') || mathNames.any { s.contains(it) }
        val hasOp = s.trim().drop(1).any { it in "+-*/^%!" } || s.trim().endsWith("!")
        if (!hasOp && !hasFn) return null
        return try {
            // The calculator app's own evaluator, so the search bar and the calculator always agree.
            val v = CalcEngine.eval(s.filterNot { it.isWhitespace() }, !rad, BigDecimal.ZERO)
            QuickAnswer("calc", "= " + CalcEngine.format(v), "$q / TAP TO COPY", CalcEngine.plain(v).replace('−', '-'))
        } catch (e: Exception) {
            null
        }
    }

    private fun fmt(d: Double): String {
        if (d == Math.floor(d) && abs(d) < 1e15) return d.toLong().toString()
        return BigDecimal(d).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }
}
