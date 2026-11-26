package app.koma.launcher.data

import android.content.Context
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Currency

class CalcError(message: String) : Exception(message)

/**
 * The maths behind the calculator. Plain arithmetic is exact (BigDecimal, 40 digits), so 0.1 + 0.2 is 0.3 and
 * 1 / 3 x 3 is 1. Functions like sin or ln go through Double and are rounded to 14 digits, which is what hides the
 * noise (sin 30 is 0.5, cos 90 is 0).
 */
object CalcEngine {
    val MC = MathContext(40)
    private val FN = MathContext(14)
    private val HUNDRED = BigDecimal(100)
    private val PI = BigDecimal("3.14159265358979323846264338327950288419716939937510")
    private val E = BigDecimal("2.71828182845904523536028747135266249775724709369995")

    /** Names the keypad can put in an expression. Longest first, so backspace can remove a whole name. */
    val functions = listOf("asin(", "acos(", "atan(", "sinh(", "cosh(", "tanh(", "sqrt(", "cbrt(", "sin(", "cos(", "tan(", "log(", "exp(", "abs(", "ln(", "√(", "∛(")

    fun eval(src: String, deg: Boolean, ans: BigDecimal): BigDecimal {
        val p = Parser(src, deg, ans)
        val v = p.expr()
        if (p.i < src.length) throw CalcError("Check the expression")
        return v
    }

    /** The number as text, 12 digits, switching to 1.5E20 style for very big and very small values. */
    fun format(v: BigDecimal, digits: Int = 12, group: Boolean = true): String {
        // Whole numbers up to 18 digits are shown in full, 100,000,000,000,000 and so on, never rounded or turned into 1E14.
        val w = v.stripTrailingZeros()
        if (w.signum() != 0 && w.scale() <= 0 && w.precision() - w.scale() <= 18) {
            val d = w.abs().toPlainString()
            return (if (w.signum() < 0) "−" else "") + if (group) groupDigits(d) else d
        }
        val r = v.round(MathContext(digits)).stripTrailingZeros()
        if (r.signum() == 0) return "0"
        val exp = r.precision() - r.scale() - 1
        if (exp >= digits || exp < -9) {
            val m = r.movePointLeft(exp).stripTrailingZeros().toPlainString()
            return m.replace('-', '−') + "E" + exp
        }
        val s = r.abs().toPlainString()
        val whole = s.substringBefore('.')
        val frac = if (s.contains('.')) "." + s.substringAfter('.') else ""
        val g = if (group) whole.reversed().chunked(3).joinToString(",").reversed() else whole
        return (if (r.signum() < 0) "−" else "") + g + frac
    }

    /** 1234567 as 1,234,567. */
    fun groupDigits(whole: String): String = whole.reversed().chunked(3).joinToString(",").reversed()

    private val numberRun = Regex("(?<![\\d.])(\\d+)(\\.\\d*)?|\\.\\d+")

    /** An expression as shown on screen: every number gets thousands separators as it is typed, 1234+5 as 1,234+5. */
    fun pretty(expr: String): String =
        numberRun.replace(expr) { m -> if (m.groups[1] == null) m.value else groupDigits(m.groupValues[1]) + m.groupValues[2] }

    /** The number as it goes back into the expression after "=". */
    fun plain(v: BigDecimal): String {
        val w = v.stripTrailingZeros()
        if (w.signum() != 0 && w.scale() <= 0 && w.precision() - w.scale() <= 18) return w.toPlainString().replace('-', '−')
        val r = v.round(MathContext(15)).stripTrailingZeros()
        return (if (r.signum() == 0) BigDecimal.ZERO else r).toPlainString().replace('-', '−')
    }

    private fun fromD(d: Double): BigDecimal {
        if (d.isNaN() || d.isInfinite()) throw CalcError("Not a number")
        if (d == 0.0) return BigDecimal.ZERO
        return BigDecimal(d).round(FN)
    }

    fun sqrt(x: BigDecimal): BigDecimal {
        if (x.signum() < 0) throw CalcError("Invalid input")
        if (x.signum() == 0) return BigDecimal.ZERO
        var g = BigDecimal(Math.sqrt(x.toDouble()))
        if (g.signum() == 0) g = BigDecimal("1E-150")
        repeat(7) { g = g.add(x.divide(g, MC)).divide(BigDecimal(2), MC) }
        return g.round(MC)
    }

    private class Parser(val s: String, val deg: Boolean, val ans: BigDecimal) {
        var i = 0
        var lastPct = false

        fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        fun expr(): BigDecimal {
            var v = term()
            var pct = lastPct
            while (true) {
                val c = peek()
                if (c == '+' || c == '−' || c == '-') {
                    i++
                    val t = term()
                    val tp = lastPct
                    val d = if (tp) v.multiply(t, MC) else t
                    v = if (c == '+') v.add(d, MC) else v.subtract(d, MC)
                    pct = false
                } else break
            }
            lastPct = pct
            return v
        }

        private fun startsPrimary(c: Char) = c.isDigit() || c == '.' || c == '(' || c == 'π' || c == '√' || c == '∛' || c.isLetter()

        private fun term(): BigDecimal {
            var v = unary()
            var pct = lastPct
            var first = true
            while (true) {
                val c = peek()
                if (c == '×' || c == '*' || c == '÷' || c == '/') {
                    i++
                    val r = unary()
                    v = if (c == '×' || c == '*') v.multiply(r, MC) else {
                        if (r.signum() == 0) throw CalcError("Can't divide by zero")
                        v.divide(r, MC)
                    }
                    pct = false
                    first = false
                } else if (startsPrimary(c)) {
                    v = v.multiply(unary(), MC)
                    pct = false
                    first = false
                } else break
            }
            lastPct = pct && first
            return v
        }

        private fun unary(): BigDecimal {
            val c = peek()
            if (c == '−' || c == '-') { i++; val v = unary(); lastPct = false; return v.negate() }
            if (c == '+') { i++; return unary() }
            return power()
        }

        private fun power(): BigDecimal {
            val base = postfix()
            if (peek() == '^') {
                i++
                val e = unary()
                lastPct = false
                return pow(base, e)
            }
            return base
        }

        private fun postfix(): BigDecimal {
            var v = primary()
            var pct = false
            while (true) {
                val c = peek()
                if (c == '!') { i++; v = factorial(v); pct = false }
                else if (c == '%') { i++; v = v.divide(HUNDRED, MC); pct = true }
                else break
            }
            lastPct = pct
            return v
        }

        private fun close() {
            if (peek() == ')') i++
        }

        private fun primary(): BigDecimal {
            val c = peek()
            if (c.isDigit() || c == '.') {
                val st = i
                while (peek().isDigit() || peek() == '.') i++
                val t = s.substring(st, i)
                if (t == "." || t.count { it == '.' } > 1) throw CalcError("Check the number")
                return BigDecimal(t)
            }
            if (c == '(') {
                i++
                val v = expr()
                close()
                return v
            }
            if (c == 'π') { i++; return PI }
            val name: String
            if (c == '√' || c == '∛') { name = c.toString(); i++ }
            else if (c.isLetter()) {
                val st = i
                while (peek().isLetter() && peek() != 'π') i++
                name = s.substring(st, i)
            } else throw CalcError("Check the expression")
            when (name) {
                "e" -> return E
                "Ans" -> return ans
            }
            val arg: BigDecimal
            if (peek() == '(') { i++; arg = expr(); close() } else arg = unary()
            lastPct = false
            return fn(name, arg)
        }

        private fun fn(name: String, x: BigDecimal): BigDecimal {
            val d = x.toDouble()
            return when (name) {
                "√", "sqrt" -> sqrt(x)
                "∛", "cbrt" -> fromD(Math.cbrt(d))
                "abs" -> x.abs()
                "exp" -> fromD(Math.exp(d))
                "ln" -> { if (x.signum() <= 0) throw CalcError("Invalid input"); fromD(Math.log(d)) }
                "log" -> { if (x.signum() <= 0) throw CalcError("Invalid input"); fromD(Math.log10(d)) }
                "sin" -> trig(d) { Math.sin(it) }
                "cos" -> trig(d) { Math.cos(it) }
                "tan" -> {
                    if (deg && Math.abs(d % 180.0) == 90.0) throw CalcError("Undefined")
                    if (!deg && Math.abs(Math.cos(d)) < 1e-15) throw CalcError("Undefined")
                    trig(d) { Math.tan(it) }
                }
                "asin" -> { if (d < -1 || d > 1) throw CalcError("Invalid input"); inv(Math.asin(d)) }
                "acos" -> { if (d < -1 || d > 1) throw CalcError("Invalid input"); inv(Math.acos(d)) }
                "atan" -> inv(Math.atan(d))
                "sinh" -> fromD(Math.sinh(d))
                "cosh" -> fromD(Math.cosh(d))
                "tanh" -> fromD(Math.tanh(d))
                else -> throw CalcError("Unknown function")
            }
        }

        private fun trig(d: Double, f: (Double) -> Double): BigDecimal {
            val r = f(if (deg) Math.toRadians(d % 360.0) else d)
            return if (Math.abs(r) < 1e-14) BigDecimal.ZERO else fromD(r)
        }

        private fun inv(r: Double): BigDecimal = fromD(if (deg) Math.toDegrees(r) else r)
    }

    private fun pow(b: BigDecimal, e: BigDecimal): BigDecimal {
        if (e.signum() == 0) return BigDecimal.ONE
        val whole = e.stripTrailingZeros().scale() <= 0
        if (whole && e.abs() <= BigDecimal(999)) {
            val n = e.toInt()
            if (n > 0) return b.pow(n, MC)
            if (b.signum() == 0) throw CalcError("Can't divide by zero")
            return BigDecimal.ONE.divide(b.pow(-n, MC), MC)
        }
        if (b.signum() < 0) throw CalcError("Invalid input")
        if (b.signum() == 0) return if (e.signum() > 0) BigDecimal.ZERO else throw CalcError("Can't divide by zero")
        return fromD(Math.pow(b.toDouble(), e.toDouble()))
    }

    private fun factorial(v: BigDecimal): BigDecimal {
        if (v.signum() < 0 || v.stripTrailingZeros().scale() > 0) throw CalcError("Whole numbers only")
        val n = v.toInt()
        if (n > 1000) throw CalcError("Limit exceeded")
        var r = BigInteger.ONE
        for (k in 2..n) r = r.multiply(BigInteger.valueOf(k.toLong()))
        return BigDecimal(r)
    }
}

/**
 * Programmer maths on a signed integer of 8, 16, 32 or 64 bits. Tokens are numbers (in the current base) and
 * the words AND OR XOR NOT LSH RSH MOD, plus + − × ÷ and brackets.
 */
object ProgEngine {
    fun wrap(v: Long, bits: Int): Long = if (bits >= 64) v else (v shl (64 - bits)) shr (64 - bits)

    fun eval(tokens: List<String>, base: Int, bits: Int): Long {
        val p = P(tokens, base, bits)
        val v = p.orE()
        if (p.i < tokens.size) throw CalcError("Check the expression")
        return wrap(v, bits)
    }

    /** The value as text in a base. Binary, octal and hex show the raw bits; decimal is signed. */
    fun show(v: Long, base: Int, bits: Int): String {
        val w = wrap(v, bits)
        if (base == 10) return w.toString()
        val mask = if (bits >= 64) -1L else (1L shl bits) - 1
        return java.lang.Long.toUnsignedString(w and mask, base).uppercase()
    }

    private class P(val t: List<String>, val base: Int, val bits: Int) {
        var i = 0
        fun peek(): String? = t.getOrNull(i)
        private fun w(v: Long) = wrap(v, bits)

        fun orE(): Long {
            var v = xorE()
            while (peek() == "OR") { i++; v = v or xorE() }
            return v
        }

        fun xorE(): Long {
            var v = andE()
            while (peek() == "XOR") { i++; v = v xor andE() }
            return v
        }

        fun andE(): Long {
            var v = shift()
            while (peek() == "AND") { i++; v = v and shift() }
            return v
        }

        fun shift(): Long {
            var v = add()
            while (true) {
                val o = peek()
                if (o == "LSH") { i++; val n = add().toInt().coerceIn(0, 64); v = if (n >= 64) 0 else w(v shl n) }
                else if (o == "RSH") { i++; val n = add().toInt().coerceIn(0, 64); v = if (n >= 64) (if (v < 0) -1 else 0) else v shr n }
                else break
            }
            return v
        }

        fun add(): Long {
            var v = mul()
            while (true) {
                val o = peek()
                if (o == "+") { i++; v = w(v + mul()) }
                else if (o == "−") { i++; v = w(v - mul()) }
                else break
            }
            return v
        }

        fun mul(): Long {
            var v = unary()
            while (true) {
                val o = peek()
                if (o == "×") { i++; v = w(v * unary()) }
                else if (o == "÷" || o == "MOD") {
                    i++
                    val r = unary()
                    if (r == 0L) throw CalcError("Can't divide by zero")
                    v = if (o == "÷") w(v / r) else v % r
                } else break
            }
            return v
        }

        fun unary(): Long {
            val o = peek()
            if (o == "NOT") { i++; return w(unary().inv()) }
            if (o == "−") { i++; return w(-unary()) }
            if (o == "(") {
                i++
                val v = orE()
                if (peek() == ")") i++
                return v
            }
            if (o == null) throw CalcError("Check the expression")
            i++
            return try {
                w(java.lang.Long.parseUnsignedLong(o, base))
            } catch (e: NumberFormatException) {
                throw CalcError("Number too large")
            }
        }
    }
}

class UnitDef(val name: String, val sym: String, val factor: BigDecimal)

class UnitSet(val name: String, val units: List<UnitDef>, val temperature: Boolean = false)

/** Unit conversions. Everything goes through one base unit per category, using exact decimal factors. */
object Converter {
    private fun u(name: String, sym: String, f: String) = UnitDef(name, sym, BigDecimal(f))

    val sets: List<UnitSet> = listOf(
        UnitSet("Length", listOf(
            u("Meter", "m", "1"), u("Kilometer", "km", "1000"), u("Centimeter", "cm", "0.01"), u("Millimeter", "mm", "0.001"),
            u("Micrometer", "µm", "0.000001"), u("Nanometer", "nm", "1E-9"), u("Mile", "mi", "1609.344"), u("Yard", "yd", "0.9144"),
            u("Foot", "ft", "0.3048"), u("Inch", "in", "0.0254"), u("Nautical mile", "nmi", "1852"),
        )),
        UnitSet("Mass", listOf(
            u("Kilogram", "kg", "1"), u("Gram", "g", "0.001"), u("Milligram", "mg", "0.000001"), u("Tonne", "t", "1000"),
            u("Pound", "lb", "0.45359237"), u("Ounce", "oz", "0.028349523125"), u("Stone", "st", "6.35029318"), u("US ton", "ton", "907.18474"),
        )),
        UnitSet("Temperature", listOf(u("Celsius", "°C", "1"), u("Fahrenheit", "°F", "1"), u("Kelvin", "K", "1")), temperature = true),
        UnitSet("Area", listOf(
            u("Square meter", "m²", "1"), u("Square kilometer", "km²", "1000000"), u("Square centimeter", "cm²", "0.0001"),
            u("Hectare", "ha", "10000"), u("Acre", "ac", "4046.8564224"), u("Square foot", "ft²", "0.09290304"),
            u("Square inch", "in²", "0.00064516"), u("Square yard", "yd²", "0.83612736"), u("Square mile", "mi²", "2589988.110336"),
        )),
        UnitSet("Volume", listOf(
            u("Liter", "L", "1"), u("Milliliter", "mL", "0.001"), u("Cubic meter", "m³", "1000"), u("US gallon", "gal", "3.785411784"),
            u("US quart", "qt", "0.946352946"), u("US pint", "pt", "0.473176473"), u("US cup", "cup", "0.2365882365"),
            u("US fluid ounce", "fl oz", "0.0295735295625"), u("Tablespoon", "tbsp", "0.01478676478125"),
            u("Teaspoon", "tsp", "0.00492892159375"), u("Imperial gallon", "imp gal", "4.54609"),
        )),
        UnitSet("Speed", listOf(
            u("Meter / second", "m/s", "1"), u("Kilometer / hour", "km/h", "0.277777777777777777777777777778"),
            u("Mile / hour", "mph", "0.44704"), u("Knot", "kn", "0.514444444444444444444444444444"),
            u("Foot / second", "ft/s", "0.3048"), u("Mach", "Ma", "340.29"),
        )),
        UnitSet("Time", listOf(
            u("Second", "s", "1"), u("Millisecond", "ms", "0.001"), u("Minute", "min", "60"), u("Hour", "h", "3600"),
            u("Day", "d", "86400"), u("Week", "wk", "604800"), u("Month (30 d)", "mo", "2592000"), u("Year (365 d)", "yr", "31536000"),
        )),
        UnitSet("Data", listOf(
            u("Bit", "bit", "0.125"), u("Byte", "B", "1"), u("Kilobyte", "KB", "1000"), u("Megabyte", "MB", "1000000"),
            u("Gigabyte", "GB", "1000000000"), u("Terabyte", "TB", "1000000000000"), u("Kibibyte", "KiB", "1024"),
            u("Mebibyte", "MiB", "1048576"), u("Gibibyte", "GiB", "1073741824"), u("Tebibyte", "TiB", "1099511627776"),
        )),
        UnitSet("Energy", listOf(
            u("Joule", "J", "1"), u("Kilojoule", "kJ", "1000"), u("Calorie", "cal", "4.184"), u("Kilocalorie", "kcal", "4184"),
            u("Watt hour", "Wh", "3600"), u("Kilowatt hour", "kWh", "3600000"), u("BTU", "BTU", "1055.05585262"),
        )),
        UnitSet("Power", listOf(
            u("Watt", "W", "1"), u("Kilowatt", "kW", "1000"), u("Megawatt", "MW", "1000000"), u("Horsepower", "hp", "745.69987158227"),
        )),
        UnitSet("Pressure", listOf(
            u("Pascal", "Pa", "1"), u("Kilopascal", "kPa", "1000"), u("Bar", "bar", "100000"), u("Atmosphere", "atm", "101325"),
            u("PSI", "psi", "6894.757293168"), u("mmHg", "mmHg", "133.322387415"),
        )),
        UnitSet("Angle", listOf(
            u("Degree", "°", "1"), u("Radian", "rad", "57.29577951308232087679815481410517"), u("Gradian", "grad", "0.9"), u("Turn", "turn", "360"),
        )),
    )

    fun convert(set: UnitSet, v: BigDecimal, from: Int, to: Int): BigDecimal {
        if (set.temperature) {
            val c = when (from) { 0 -> v; 1 -> v.subtract(BigDecimal(32)).multiply(BigDecimal(5)).divide(BigDecimal(9), CalcEngine.MC); else -> v.subtract(BigDecimal("273.15")) }
            return when (to) { 0 -> c; 1 -> c.multiply(BigDecimal(9)).divide(BigDecimal(5), CalcEngine.MC).add(BigDecimal(32)); else -> c.add(BigDecimal("273.15")) }
        }
        return v.multiply(set.units[from].factor, CalcEngine.MC).divide(set.units[to].factor, CalcEngine.MC)
    }
}

/**
 * Live exchange rates. They come from free, keyless services (open.er-api.com first, then two mirrors of a
 * community data set), are saved on the phone, and are reused when there is no connection.
 */
object Rates {
    var rates: Map<String, BigDecimal> = emptyMap()
        private set
    var updated: Long = 0L
        private set
    private var loaded = false

    private val sources = listOf(
        "https://open.er-api.com/v6/latest/USD",
        "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json",
        "https://latest.currency-api.pages.dev/v1/currencies/usd.json",
    )

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val o = JSONObject(JsonFile(ctx, "rates.json").read() ?: return)
            updated = o.optLong("t")
            rates = parse(o.getJSONObject("r"))
        } catch (e: Exception) {
        }
    }

    private fun parse(o: JSONObject): Map<String, BigDecimal> {
        val m = HashMap<String, BigDecimal>()
        o.keys().forEach { k ->
            val code = k.uppercase()
            try {
                Currency.getInstance(code)
                val v = BigDecimal(o.get(k).toString())
                if (v.signum() > 0) m[code] = v
            } catch (e: Exception) {
            }
        }
        m["USD"] = BigDecimal.ONE
        return m
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Blocking; call from a background thread. True when fresh rates were saved. */
    fun refresh(ctx: Context): Boolean {
        for (url in sources) {
            try {
                val o = JSONObject(get(url))
                val obj = if (o.has("rates")) o.getJSONObject("rates") else o.getJSONObject("usd")
                val m = parse(obj)
                if (m.size < 20) continue
                rates = m
                updated = System.currentTimeMillis()
                val r = JSONObject()
                m.forEach { (k, v) -> r.put(k, v.toPlainString()) }
                JsonFile(ctx, "rates.json").write(JSONObject().put("t", updated).put("r", r).toString())
                return true
            } catch (e: Exception) {
            }
        }
        return false
    }

    fun codes(): List<String> = rates.keys.sorted()

    fun name(code: String): String = try { Currency.getInstance(code).displayName } catch (e: Exception) { code }

    fun convert(v: BigDecimal, from: String, to: String): BigDecimal? {
        val a = rates[from] ?: return null
        val b = rates[to] ?: return null
        return v.divide(a, CalcEngine.MC).multiply(b, CalcEngine.MC)
    }
}
