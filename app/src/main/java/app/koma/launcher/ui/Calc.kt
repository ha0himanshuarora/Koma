package app.koma.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.koma.launcher.data.CalcEngine
import app.koma.launcher.data.CalcError
import app.koma.launcher.data.Converter
import app.koma.launcher.data.JsonFile
import app.koma.launcher.data.ProgEngine
import app.koma.launcher.data.Rates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale

// ---- the model ---------------------------------------------------------------------------------

private const val OPS = "+−×÷^"

/** Everything the calculator remembers while it is open. History is saved; the rest starts fresh each time. */
private class CalcModel(val ctx: Context) {
    var mode by mutableIntStateOf(0)

    // basic + scientific
    var expr by mutableStateOf("")
    var shown by mutableStateOf("")
    var justEval by mutableStateOf(false)
    var err by mutableStateOf("")
    var deg by mutableStateOf(true)
    var inv by mutableStateOf(false)
    var ans by mutableStateOf(BigDecimal.ZERO)
    var mem by mutableStateOf(BigDecimal.ZERO)
    var memSet by mutableStateOf(false)
    val history = mutableStateListOf<Pair<String, String>>()
    private val file = JsonFile(ctx, "calc.json")

    // programmer
    var pbase by mutableIntStateOf(10)
    var pbits by mutableIntStateOf(64)
    val ptoks = mutableStateListOf<String>()
    var pJust by mutableStateOf(false)

    // converter
    var cat by mutableIntStateOf(0) // 0 is currency, then Converter.sets
    var fromU by mutableIntStateOf(0)
    var toU by mutableIntStateOf(1)
    var fromC by mutableStateOf("USD")
    var toC by mutableStateOf(localCurrency())
    var amount by mutableStateOf("1")
    var stamp by mutableLongStateOf(0L)
    var loading by mutableStateOf(false)
    var offline by mutableStateOf(false)

    init { loadHistory() }

    /** Reads the saved history again, so the home tile and the app never overwrite each other's entries. */
    private fun loadHistory() {
        try {
            val a = JSONArray(file.read() ?: "[]")
            val list = ArrayList<Pair<String, String>>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(o.getString("e") to o.getString("r"))
            }
            history.clear()
            history.addAll(list)
        } catch (e: Exception) {
        }
    }

    private fun localCurrency(): String = try {
        java.util.Currency.getInstance(Locale.getDefault()).currencyCode.takeIf { it != "USD" } ?: "EUR"
    } catch (e: Exception) {
        "EUR"
    }

    private fun save() {
        try {
            val a = JSONArray()
            history.take(60).forEach { a.put(JSONObject().put("e", it.first).put("r", it.second)) }
            file.write(a.toString())
        } catch (e: Exception) {
        }
    }

    fun clearHistory() { history.clear(); save() }

    // -- basic and scientific --

    fun result(): BigDecimal? = try { if (expr.isEmpty()) null else CalcEngine.eval(expr, deg, ans) } catch (e: Exception) { null }

    private fun fresh() {
        err = ""
        if (justEval) { expr = ""; justEval = false }
    }

    private fun carry() {
        err = ""
        if (justEval) { expr = CalcEngine.plain(ans); justEval = false }
    }

    private fun lastNum(): String {
        var j = expr.length
        while (j > 0 && (expr[j - 1].isDigit() || expr[j - 1] == '.')) j--
        return expr.substring(j)
    }

    fun digit(d: String) { fresh(); expr += d }

    fun dot() {
        fresh()
        val n = lastNum()
        if (n.contains('.')) return
        expr += if (n.isEmpty()) "0." else "."
    }

    fun op(o: String) {
        carry()
        if (expr.isEmpty()) { expr = if (o == "−") "−" else "0$o"; return }
        val last = expr.last()
        if (expr == "−" || (expr.length >= 2 && expr[expr.length - 2] == '(' && last == '−')) return
        if (last in OPS) {
            expr = if (o == "−" && last in "×÷^") expr + o
            else if (expr.length >= 2 && expr[expr.length - 2] in OPS && last == '−') expr.dropLast(2) + o
            else expr.dropLast(1) + o
        } else if (last == '(' && o != "−") return
        else expr += o
    }

    fun func(name: String) { fresh(); expr += name }

    fun constant(c: String) { fresh(); expr += c }

    fun openParen() { fresh(); expr += "(" }

    fun closeParen() {
        err = ""
        val opens = expr.count { it == '(' }
        val closes = expr.count { it == ')' }
        val last = expr.lastOrNull() ?: return
        if (opens > closes && last !in OPS && last != '(') expr += ")"
    }

    /** Postfix keys: % ! ^2 and so on. They carry on from the last answer. */
    fun suffix(s: String) {
        carry()
        val last = expr.lastOrNull() ?: return
        if (last.isDigit() || last == ')' || last == 'π' || last == 'e' || last == '%' || last == '!' || expr.endsWith("Ans")) expr += s
    }

    fun expKey() {
        if (expr.isEmpty() && !justEval) return
        op("×")
        expr += "10^"
    }

    fun back() {
        err = ""
        justEval = false
        if (expr.isEmpty()) return
        val f = (CalcEngine.functions + "Ans").firstOrNull { expr.endsWith(it) }
        expr = expr.dropLast(f?.length ?: 1)
    }

    fun clear() { expr = ""; shown = ""; justEval = false; err = "" }

    fun sign() {
        carry()
        if (expr.isEmpty()) { expr = "−"; return }
        val n = lastNum()
        if (n.isEmpty()) return
        val st = expr.length - n.length
        val b = if (st > 0) expr[st - 1] else ' '
        val bb = if (st > 1) expr[st - 2] else ' '
        expr = when {
            b == '−' && (st == 1 || bb in "(×÷^") -> expr.removeRange(st - 1, st)
            b == '−' -> expr.replaceRange(st - 1, st, "+")
            b == '+' -> expr.replaceRange(st - 1, st, "−")
            else -> expr.substring(0, st) + "−" + n
        }
    }

    fun equals() {
        if (expr.isEmpty() || justEval) return
        try {
            val v = CalcEngine.eval(expr, deg, ans)
            loadHistory()
            history.add(0, expr to CalcEngine.format(v))
            while (history.size > 60) history.removeAt(history.size - 1)
            save()
            shown = expr
            ans = v
            expr = CalcEngine.plain(v)
            justEval = true
            err = ""
        } catch (e: CalcError) {
            err = e.message ?: "Error"
        } catch (e: Exception) {
            err = "Error"
        }
    }

    private fun current(): BigDecimal? = if (justEval) ans else result()

    fun memClear() { mem = BigDecimal.ZERO; memSet = false }

    fun memRecall() {
        if (!memSet) return
        fresh()
        val t = CalcEngine.plain(mem)
        expr += if (mem.signum() < 0) "($t)" else t
    }

    fun memAdd(sign: Int) {
        val v = current() ?: return
        mem = if (sign > 0) mem.add(v) else mem.subtract(v)
        memSet = true
    }

    fun insertResult(text: String) {
        fresh()
        expr += text.replace(",", "")
    }

    // -- programmer --

    private val binOps = setOf("AND", "OR", "XOR", "LSH", "RSH", "MOD", "+", "−", "×", "÷")
    private val allOps = binOps + setOf("NOT", "(", ")")

    fun pval(): Long? {
        var list = ptoks.toList()
        while (list.isNotEmpty()) {
            try { return ProgEngine.eval(list, pbase, pbits) } catch (e: Exception) { list = list.dropLast(1) }
        }
        return null
    }

    private fun tokensFor(v: Long): List<String> {
        val s = ProgEngine.show(v, pbase, pbits)
        return if (pbase == 10 && s.startsWith("-")) listOf("−", s.substring(1)) else listOf(s)
    }

    fun pDigit(c: String) {
        err = ""
        if (pJust) { ptoks.clear(); pJust = false }
        val l = ptoks.lastOrNull()
        if (l != null && l !in allOps) ptoks[ptoks.size - 1] = l + c else ptoks.add(c)
        val maxLen = when (pbase) { 2 -> pbits; 8 -> (pbits + 2) / 3; 16 -> pbits / 4; else -> when (pbits) { 64 -> 19; 32 -> 10; 16 -> 5; else -> 3 } }
        if (ptoks.last().length > maxLen) ptoks[ptoks.size - 1] = ptoks.last().dropLast(1)
    }

    fun pOp(o: String) {
        err = ""
        pJust = false
        val l = ptoks.lastOrNull()
        when (o) {
            "NOT", "(" -> ptoks.add(o)
            ")" -> if (ptoks.count { it == "(" } > ptoks.count { it == ")" } && l != null && l !in binOps && l != "(" && l != "NOT") ptoks.add(o)
            else -> {
                if (l == null) ptoks.add("0")
                else if (l in binOps) {
                    if (o == "−") { ptoks.add(o); return }
                    ptoks.removeAt(ptoks.size - 1)
                } else if (l == "NOT" || l == "(") { if (o == "−") ptoks.add(o); return }
                ptoks.add(o)
            }
        }
    }

    fun pBack() {
        err = ""
        pJust = false
        val l = ptoks.lastOrNull() ?: return
        if (l !in allOps && l.length > 1) ptoks[ptoks.size - 1] = l.dropLast(1) else ptoks.removeAt(ptoks.size - 1)
    }

    fun pClear() { ptoks.clear(); pJust = false; err = "" }

    fun pEquals() {
        if (ptoks.isEmpty()) return
        try {
            val v = ProgEngine.eval(ptoks.toList(), pbase, pbits)
            ptoks.clear()
            ptoks.addAll(tokensFor(v))
            pJust = true
            err = ""
        } catch (e: CalcError) {
            err = e.message ?: "Error"
        } catch (e: Exception) {
            err = "Error"
        }
    }

    fun pSetBase(b: Int) {
        val v = pval()
        pbase = b
        ptoks.clear()
        if (v != null) ptoks.addAll(tokensFor(v))
        pJust = true
    }

    fun pSetBits(b: Int) {
        val v = pval()
        pbits = b
        ptoks.clear()
        if (v != null) ptoks.addAll(tokensFor(ProgEngine.wrap(v, b)))
        pJust = true
    }

    // -- converter --

    fun amountValue(): BigDecimal = try { BigDecimal(amount.replace('−', '-').ifEmpty { "0" }) } catch (e: Exception) { BigDecimal.ZERO }

    fun amountKey(k: String) {
        when (k) {
            "AC" -> amount = ""
            "⌫" -> amount = amount.dropLast(1)
            "±" -> amount = if (amount.startsWith("−")) amount.drop(1) else if (amount.isEmpty()) "−" else "−$amount"
            "." -> if (!amount.contains('.')) amount += if (amount.isEmpty() || amount == "−") "0." else "."
            else -> if (amount.length < 16) amount = if (amount == "0") k else amount + k
        }
    }

    fun convert(): BigDecimal? {
        if (stamp < 0) return null
        val v = amountValue()
        return if (cat == 0) Rates.convert(v, fromC, toC) else Converter.convert(Converter.sets[cat - 1], v, fromU, toU)
    }

    fun swap() {
        if (cat == 0) { val t = fromC; fromC = toC; toC = t } else { val t = fromU; fromU = toU; toU = t }
    }

    fun pickCat(i: Int) {
        cat = i
        fromU = 0
        toU = 1
    }

    suspend fun refresh() {
        loading = true
        val ok = withContext(Dispatchers.IO) { Rates.refresh(ctx) }
        offline = !ok
        stamp = Rates.updated
        loading = false
    }
}

// ---- home screen tile --------------------------------------------------------------------------

/**
 * The calculator on the home screen, in the tile's own colours. It works in place and shares everything with the
 * app. Wide, short sizes put the display beside the keys; tall ones stack them. Tapping the display opens the app,
 * holding it copies the answer, and holding backspace clears.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalcTile(w: Int, h: Int, bg: Color, ink: Color, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val m = remember { sharedCalc(ctx) }
    val live = if (m.justEval || m.err.isNotEmpty()) null else m.result()
    val hasOp = m.expr.any { it in "+−×÷^%!" } && !(m.expr.startsWith("−") && m.expr.drop(1).all { it.isDigit() || it == '.' })
    val big = if (m.justEval) CalcEngine.format(m.ans) else m.expr.ifEmpty { "0" }
    val last = m.history.firstOrNull()
    val small = when {
        m.err.isNotEmpty() -> m.err
        m.justEval -> m.shown + " ="
        live != null && hasOp -> "= " + CalcEngine.format(live)
        m.expr.isEmpty() && last != null -> last.first + " = " + last.second
        else -> ""
    }
    val split = h <= 2 && w >= 3
    BoxWithConstraints(Modifier.fillMaxSize().clip(TileShape).background(bg)) {
        val pad = if (maxWidth > 250.dp) 14.dp else 10.dp
        val gap = 4.dp
        val rowsN = if (split) 4 else 5
        val keysW = if (split) (maxWidth - pad * 2) * 0.58f else maxWidth - pad * 2
        val keysH = if (split) maxHeight - pad * 2 else (maxHeight - pad * 2) * 0.64f
        val keyH = (keysH - gap * (rowsN - 1)) / rowsN
        val keyW = (keysW - gap * 3) / 4
        val ts = (minOf(keyH.value, keyW.value) * 0.42f).coerceIn(13f, 22f).toInt()

        @Composable
        fun RowScope.K(label: String, kind: Int = 0, glyph: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
            // 0 digits sit bare on the tile, 1 are the tools, 2 the operators, 3 the equals key.
            val fill = when (kind) { 3 -> ink; 2 -> ink.copy(alpha = 0.18f); 1 -> ink.copy(alpha = 0.08f); else -> Color.Transparent }
            val fg = if (kind == 3) bg else ink
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(50)).background(fill)
                    .combinedClickable(onLongClick = onLong, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (glyph != null) Glyph(glyph, fg, (ts + 2).dp, 2.2f)
                else BasicText(label, style = bodyStyle(fg, if (kind >= 2) ts + 2 else ts, medium = kind != 0), maxLines = 1)
            }
        }

        @Composable
        fun Display(mod: Modifier) {
            Column(
                mod.combinedClickable(onLongClick = { copy(ctx, if (m.justEval) CalcEngine.format(m.ans, 15, false) else m.expr) }, onClick = onOpen)
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText("CALCULATOR" + if (m.memSet) "  /  M" else "", style = label(ink.copy(alpha = 0.7f), 10.sp, bold = true), maxLines = 1)
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End) {
                    if (small.isNotEmpty()) BasicText(small, style = monoStyle(ink.copy(alpha = 0.6f), 10), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    FitText(big, ink, if (split) 34 else 44, Modifier.fillMaxWidth().weight(if (split) 1f else 1.5f))
                }
            }
        }

        @Composable
        fun Keys(mod: Modifier) {
            val rows = if (split) listOf(
                listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("0", ".", "=", "+"),
            ) else listOf(
                listOf("AC", "back", "%", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"), listOf("±", "0", ".", "="),
            )
            Column(mod, verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { k ->
                            when (k) {
                                "AC" -> K("AC", 1) { m.clear() }
                                "back" -> K("", 1, "backspace", onLong = { m.clear() }) { m.back() }
                                "%" -> K("%", 1) { m.suffix("%") }
                                "÷", "×", "−", "+" -> K(k, 2) { m.op(k) }
                                "±" -> K("±", 1) { m.sign() }
                                "." -> K(".") { m.dot() }
                                "=" -> K("=", 3) { m.equals() }
                                else -> K(k) { m.digit(k) }
                            }
                        }
                    }
                }
            }
        }

        if (split) {
            Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(0.42f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Display(Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().height(keyH), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        K("AC", 1) { m.clear() }
                        K("", 1, "backspace", onLong = { m.clear() }) { m.back() }
                    }
                }
                Keys(Modifier.weight(0.58f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.spacedBy(gap)) {
                Display(Modifier.fillMaxWidth().weight(1f))
                Keys(Modifier.fillMaxWidth().fillMaxHeight(0.64f))
            }
        }
    }
}

private var sharedModel: CalcModel? = null

/** The one calculator, used by both the home tile and the app, so what you type in one is there in the other. */
private fun sharedCalc(ctx: Context): CalcModel = sharedModel ?: CalcModel(ctx.applicationContext).also { sharedModel = it }

// ---- screen ------------------------------------------------------------------------------------

private val modeNames = listOf("BASIC", "SCIENTIFIC", "PROGRAMMER", "CONVERT")

@Composable
fun CalcApp(state: HomeState) = CalcBody(state)

/** One line of display text that shrinks until it fits both the width and the height it is given, so no digit is cut off. */
@Composable
private fun FitText(text: String, color: Color, top: Int, modifier: Modifier = Modifier, min: Int = 14) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterEnd) {
        val cap = if (maxHeight.value.isFinite()) minOf(top.toFloat(), maxHeight.value * 0.62f) else top.toFloat()
        var size by remember(text, cap) { mutableFloatStateOf(cap.coerceAtLeast(min.toFloat())) }
        val sp = with(LocalDensity.current) { size.dp.toSp() }
        BasicText(
            text, style = dispStyle(color, 10).copy(fontSize = sp), maxLines = 1, softWrap = false,
            onTextLayout = { if (it.didOverflowWidth && size > min) size = (size * 0.9f).coerceAtLeast(min.toFloat()) },
        )
    }
}

@Composable
private fun CalcBody(state: HomeState) {
    val pal = state.palette
    val ctx = LocalContext.current
    val m = remember { sharedCalc(ctx) }
    var history by remember { mutableStateOf(false) }
    var picking by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { Rates.load(ctx) }
        m.stamp = Rates.updated
    }
    LaunchedEffect(m.mode, m.cat) {
        if (m.mode == 3 && m.cat == 0 && System.currentTimeMillis() - Rates.updated > 3 * 3_600_000L) m.refresh()
    }

    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    @Composable
    fun ModeTabs(modifier: Modifier) {
        Row(modifier.clip(CircleShape).background(deepBg()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                modeNames.forEachIndexed { i, name ->
                    val on = i == m.mode
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent).clickable { m.mode = i },
                        contentAlignment = Alignment.Center,
                    ) { BasicText(name, style = monoStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.55f), 8, bold = on), maxLines = 1) }
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn("back") { state.back() }
                Column(Modifier.weight(if (land) 0.55f else 1f).padding(start = 14.dp)) {
                    BasicText("Calculator", style = dispStyle(pal.onGround, 30), maxLines = 1)
                    BasicText(modeNames[m.mode] + if (m.mode < 2 && m.memSet) "  /  M" else "", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
                }
                if (land) ModeTabs(Modifier.weight(1.6f).padding(horizontal = 12.dp))
                if (m.mode == 3 && m.cat == 0) {
                    CircleBtn("refresh") { scope.launch { m.refresh() } }
                } else if (m.mode < 2) {
                    CircleBtn("clock") { history = true }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (m.mode) {
                    0, 1 -> CalcPad(m, sci = m.mode == 1)
                    2 -> ProgPad(m, land)
                    else -> ConvertPage(m, land) { picking = it }
                }
            }
            if (!land) PillNav(listOf("calc" to "BASIC", "bolt" to "SCIENTIFIC", "widget" to "PROGRAMMER", "swap" to "CONVERT"), m.mode) { m.mode = it }
            else Spacer(Modifier.height(10.dp))
        }
        if (history) {
            HistoryPage(m, { history = false })
        }
        if (picking != 0) {
            UnitPicker(m, picking == 1) { picking = 0 }
        }
    }
}

// ---- keys --------------------------------------------------------------------------------------

/** kind: 0 number, 1 operator, 2 equals, 3 small function, 4 strong, 5 plain text. */
@Composable
private fun RowScope.Key(label: String, kind: Int = 0, size: Int = 24, glyph: String? = null, on: Boolean = false, enabled: Boolean = true, weight: Float = 1f, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val bg = when {
        on -> pal.tiles[0]
        kind == 0 -> cardBg()
        kind == 1 -> surfaceColor(0.16f)
        kind == 2 -> pal.tiles[0]
        kind == 3 -> deepBg()
        kind == 4 -> pal.onGround
        else -> Color.Transparent
    }
    val fg = when {
        on || kind == 2 -> pal.ink
        kind == 4 -> pal.ground
        kind == 3 || kind == 5 -> pal.onGround.copy(alpha = 0.85f)
        else -> pal.onGround
    }
    Box(
        Modifier.weight(weight).fillMaxHeight().clip(RoundedCornerShape(50)).background(bg).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val a = if (enabled) 1f else 0.3f
        if (glyph != null) Glyph(glyph, fg.copy(alpha = a), 24.dp, 2.2f)
        else BasicText(label, style = if (kind >= 3) monoStyle(fg.copy(alpha = a), size, bold = true) else bodyStyle(fg.copy(alpha = a), size, medium = true), maxLines = 1)
    }
}

@Composable
private fun KeyRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}


private fun copy(ctx: Context, text: String) {
    try {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Koma", text))
        // Android 13 and later show their own "copied" confirmation, so Koma adds one only on older versions.
        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
    }
}

// ---- basic and scientific ----------------------------------------------------------------------

@Composable
private fun CalcPad(m: CalcModel, sci: Boolean) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val live = if (m.justEval || m.err.isNotEmpty()) null else m.result()
    val hasOp = m.expr.any { it in "+−×÷^%!" || it.isLetter() || it == '√' || it == '∛' || it == '(' } && !(m.expr.startsWith("−") && m.expr.drop(1).all { it.isDigit() || it == '.' })
    val big = if (m.justEval) CalcEngine.format(m.ans) else m.expr.ifEmpty { "0" }
    val small = when {
        m.err.isNotEmpty() -> m.err
        m.justEval -> m.shown + " ="
        live != null && hasOp -> "= " + CalcEngine.format(live)
        else -> ""
    }
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }

    @Composable
    fun DisplayBox(mod: Modifier) {
        Column(
            mod.fillMaxWidth().clip(Big).background(surfaceColor(0.05f)).padding(horizontal = 22.dp, vertical = 14.dp)
                .pointerInput(m.justEval, m.ans) { detectTapGestures(onLongPress = { copy(ctx, if (m.justEval) CalcEngine.format(m.ans, 15, false) else m.expr) }) },
            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End,
        ) {
            if (sci) BasicText(if (m.deg) "DEG" else "RAD", style = monoStyle(pal.onGround.copy(alpha = 0.45f), 10), modifier = Modifier.align(Alignment.Start))
            BasicText(small, style = bodyStyle(pal.onGround.copy(alpha = if (m.err.isNotEmpty()) 0.9f else 0.5f), 18), maxLines = 1, overflow = TextOverflow.Ellipsis)
            FitText(big, pal.onGround, 52, Modifier.fillMaxWidth().weight(1f))
        }
    }

    @Composable
    fun MemRow() {
        KeyRow(Modifier.height(32.dp)) {
            Key("MC", 5, 11, enabled = m.memSet) { m.memClear() }
            Key("MR", 5, 11, enabled = m.memSet) { m.memRecall() }
            Key("M+", 5, 11) { m.memAdd(1) }
            Key("M−", 5, 11) { m.memAdd(-1) }
        }
    }

    @Composable
    fun KeysBlock(mod: Modifier) {
        Column(mod.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (sci) {
                val i = m.inv
                KeyRow(Modifier.weight(0.8f)) {
                    Key(if (m.deg) "DEG" else "RAD", 3, 11) { m.deg = !m.deg }
                    Key("INV", 3, 11, on = i) { m.inv = !m.inv }
                    Key("π", 3, 14) { m.constant("π") }
                    Key("e", 3, 14) { m.constant("e") }
                    Key("EXP", 3, 11) { m.expKey() }
                }
                KeyRow(Modifier.weight(0.8f)) {
                    Key(if (i) "sin⁻¹" else "sin", 3, 11) { m.func(if (i) "asin(" else "sin(") }
                    Key(if (i) "cos⁻¹" else "cos", 3, 11) { m.func(if (i) "acos(" else "cos(") }
                    Key(if (i) "tan⁻¹" else "tan", 3, 11) { m.func(if (i) "atan(" else "tan(") }
                    Key("(", 3, 14) { m.openParen() }
                    Key(")", 3, 14) { m.closeParen() }
                }
                KeyRow(Modifier.weight(0.8f)) {
                    Key(if (i) "eˣ" else "ln", 3, 12) { m.func(if (i) "exp(" else "ln(") }
                    Key(if (i) "10ˣ" else "log", 3, 12) { if (i) { m.func("10^("); } else m.func("log(") }
                    Key("√", 3, 14) { m.func("√(") }
                    Key("xʸ", 3, 12) { m.op("^") }
                    Key("x!", 3, 12) { m.suffix("!") }
                }
                KeyRow(Modifier.weight(0.8f)) {
                    Key("1/x", 3, 11) { m.suffix("^(−1)") }
                    Key("x²", 3, 12) { m.suffix("^2") }
                    Key("|x|", 3, 12) { m.func("abs(") }
                    Key("∛", 3, 14) { m.func("∛(") }
                    Key("Ans", 3, 11) { m.constant("Ans") }
                }
            }
            val w = if (sci) 1.05f else 1f
            KeyRow(Modifier.weight(w)) {
                Key("AC", 4, 20) { m.clear() }
                Key("", 1, glyph = "backspace") { m.back() }
                Key("%", 1, 22) { m.suffix("%") }
                Key("÷", 1, 26) { m.op("÷") }
            }
            KeyRow(Modifier.weight(w)) {
                Key("7") { m.digit("7") }; Key("8") { m.digit("8") }; Key("9") { m.digit("9") }; Key("×", 1, 26) { m.op("×") }
            }
            KeyRow(Modifier.weight(w)) {
                Key("4") { m.digit("4") }; Key("5") { m.digit("5") }; Key("6") { m.digit("6") }; Key("−", 1, 26) { m.op("−") }
            }
            KeyRow(Modifier.weight(w)) {
                Key("1") { m.digit("1") }; Key("2") { m.digit("2") }; Key("3") { m.digit("3") }; Key("+", 1, 26) { m.op("+") }
            }
            KeyRow(Modifier.weight(w)) {
                Key("±", 1, 22) { m.sign() }; Key("0") { m.digit("0") }; Key(".") { m.dot() }; Key("=", 2, 28) { m.equals() }
            }
        }
    }

    if (land) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DisplayBox(Modifier.weight(1f))
                MemRow()
            }
            KeysBlock(Modifier.weight(if (sci) 1.9f else 1.3f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DisplayBox(Modifier.weight(if (sci) 0.9f else 1.1f))
            MemRow()
            KeysBlock(Modifier.weight(if (sci) 3.5f else 2.4f))
        }
    }
}

// ---- programmer --------------------------------------------------------------------------------

@Composable
private fun ProgPad(m: CalcModel, land: Boolean) {
    val pal = LocalPalette.current
    val v = m.pval()
    val text = m.ptoks.joinToString(" ").ifEmpty { "0" }

    @Composable
    fun Sizes() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(64 to "QWORD", 32 to "DWORD", 16 to "WORD", 8 to "BYTE").forEach { (b, n) ->
                val on = m.pbits == b
                Box(
                    Modifier.weight(1f).height(if (land) 28.dp else 30.dp).clip(CircleShape).background(if (on) pal.onGround else cardBg()).clickable { m.pSetBits(b) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(n, style = monoStyle(if (on) pal.ground else pal.onGround.copy(alpha = 0.7f), 9, bold = true)) }
            }
        }
    }

    @Composable
    fun Bases() {
        Column(Modifier.fillMaxWidth().clip(Big).background(surfaceColor(0.05f)).padding(vertical = if (land) 3.dp else 6.dp)) {
            listOf(16 to "HEX", 10 to "DEC", 8 to "OCT", 2 to "BIN").forEach { (b, n) ->
                val on = m.pbase == b
                val s = if (v == null) "0" else ProgEngine.show(v, b, m.pbits).let { if (b == 2) it.reversed().chunked(4).joinToString(" ").reversed() else it }
                Row(
                    Modifier.fillMaxWidth().clickable { m.pSetBase(b) }.padding(horizontal = 18.dp, vertical = if (land) 3.dp else 5.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    BasicText(n, style = monoStyle(if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.5f), 10, bold = true), modifier = Modifier.width(40.dp).padding(top = 3.dp))
                    BasicText(s, style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.7f), if (b == 2) 11 else if (land) 13 else 15, bold = on), maxLines = if (land && b == 2) 1 else 2, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    @Composable
    fun Entry(mod: Modifier) {
        Column(
            mod.fillMaxWidth().clip(Big).background(surfaceColor(0.05f)).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.End,
        ) {
            if (m.err.isNotEmpty()) BasicText(m.err, style = bodyStyle(pal.onGround.copy(alpha = 0.8f), 14), maxLines = 1)
            FitText(text, pal.onGround, if (land) 28 else 34, Modifier.fillMaxWidth().weight(1f))
        }
    }

    val hex = m.pbase == 16
    fun dg(c: String) = c.toInt(16) < m.pbase

    if (land) {
        // Sideways: the readouts on the left, a wide eight-by-four keypad on the right.
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Sizes()
                Bases()
                Entry(Modifier.weight(1f))
            }
            Column(Modifier.weight(1.45f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                KeyRow(Modifier.weight(1f)) {
                    Key("A", 3, 14, enabled = hex) { m.pDigit("A") }; Key("B", 3, 14, enabled = hex) { m.pDigit("B") }
                    Key("C", 3, 14, enabled = hex) { m.pDigit("C") }; Key("D", 3, 14, enabled = hex) { m.pDigit("D") }
                    Key("E", 3, 14, enabled = hex) { m.pDigit("E") }; Key("F", 3, 14, enabled = hex) { m.pDigit("F") }
                    Key("(", 3, 14) { m.pOp("(") }; Key(")", 3, 14) { m.pOp(")") }
                }
                KeyRow(Modifier.weight(1f)) {
                    Key("7", size = 20, enabled = dg("7")) { m.pDigit("7") }; Key("8", size = 20, enabled = dg("8")) { m.pDigit("8") }
                    Key("9", size = 20, enabled = dg("9")) { m.pDigit("9") }; Key("÷", 1, 20) { m.pOp("÷") }
                    Key("AND", 3, 9) { m.pOp("AND") }; Key("OR", 3, 9) { m.pOp("OR") }
                    Key("XOR", 3, 9) { m.pOp("XOR") }; Key("NOT", 3, 9) { m.pOp("NOT") }
                }
                KeyRow(Modifier.weight(1f)) {
                    Key("4", size = 20, enabled = dg("4")) { m.pDigit("4") }; Key("5", size = 20, enabled = dg("5")) { m.pDigit("5") }
                    Key("6", size = 20, enabled = dg("6")) { m.pDigit("6") }; Key("×", 1, 20) { m.pOp("×") }
                    Key("LSH", 3, 9) { m.pOp("LSH") }; Key("RSH", 3, 9) { m.pOp("RSH") }
                    Key("MOD", 3, 9) { m.pOp("MOD") }; Key("", 1, glyph = "backspace") { m.pBack() }
                }
                KeyRow(Modifier.weight(1f)) {
                    Key("1", size = 20, enabled = dg("1")) { m.pDigit("1") }; Key("2", size = 20, enabled = dg("2")) { m.pDigit("2") }
                    Key("3", size = 20, enabled = dg("3")) { m.pDigit("3") }; Key("0", size = 20) { m.pDigit("0") }
                    Key("−", 1, 20) { m.pOp("−") }; Key("+", 1, 20) { m.pOp("+") }
                    Key("AC", 4, 14) { m.pClear() }; Key("=", 2, 22) { m.pEquals() }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Sizes()
        Bases()
        Entry(Modifier.weight(0.5f))
        Column(Modifier.weight(3f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyRow(Modifier.weight(1f)) {
                Key("A", 3, 16, enabled = hex) { m.pDigit("A") }; Key("B", 3, 16, enabled = hex) { m.pDigit("B") }
                Key("(", 3, 16) { m.pOp("(") }; Key(")", 3, 16) { m.pOp(")") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("C", 3, 16, enabled = hex) { m.pDigit("C") }; Key("D", 3, 16, enabled = hex) { m.pDigit("D") }
                Key("AND", 3, 11) { m.pOp("AND") }; Key("OR", 3, 11) { m.pOp("OR") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("E", 3, 16, enabled = hex) { m.pDigit("E") }; Key("F", 3, 16, enabled = hex) { m.pDigit("F") }
                Key("XOR", 3, 11) { m.pOp("XOR") }; Key("NOT", 3, 11) { m.pOp("NOT") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("LSH", 3, 11) { m.pOp("LSH") }; Key("RSH", 3, 11) { m.pOp("RSH") }
                Key("MOD", 3, 11) { m.pOp("MOD") }; Key("÷", 1, 24) { m.pOp("÷") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("7", enabled = dg("7")) { m.pDigit("7") }; Key("8", enabled = dg("8")) { m.pDigit("8") }
                Key("9", enabled = dg("9")) { m.pDigit("9") }; Key("×", 1, 24) { m.pOp("×") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("4", enabled = dg("4")) { m.pDigit("4") }; Key("5", enabled = dg("5")) { m.pDigit("5") }
                Key("6", enabled = dg("6")) { m.pDigit("6") }; Key("−", 1, 24) { m.pOp("−") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("1", enabled = dg("1")) { m.pDigit("1") }; Key("2", enabled = dg("2")) { m.pDigit("2") }
                Key("3", enabled = dg("3")) { m.pDigit("3") }; Key("+", 1, 24) { m.pOp("+") }
            }
            KeyRow(Modifier.weight(1f)) {
                Key("AC", 4, 18) { m.pClear() }; Key("0") { m.pDigit("0") }
                Key("", 1, glyph = "backspace") { m.pBack() }; Key("=", 2, 26) { m.pEquals() }
            }
        }
    }
}

// ---- converter ---------------------------------------------------------------------------------

private fun ago(t: Long): String {
    val min = (System.currentTimeMillis() - t) / 60_000L
    return when {
        t <= 0L -> "NO RATES YET"
        min < 1 -> "UPDATED JUST NOW"
        min < 60 -> "UPDATED ${min}M AGO"
        min < 1440 -> "UPDATED ${min / 60}H AGO"
        else -> "UPDATED ${min / 1440}D AGO"
    }
}

@Composable
private fun ConvertPage(m: CalcModel, land: Boolean, onPick: (Int) -> Unit) {
    val pal = LocalPalette.current
    val ctx = LocalContext.current
    val cats = listOf("Currency") + Converter.sets.map { it.name }
    val stamp = m.stamp
    val out = m.convert()
    val fromLabel = if (m.cat == 0) m.fromC else Converter.sets[m.cat - 1].units[m.fromU].sym
    val toLabel = if (m.cat == 0) m.toC else Converter.sets[m.cat - 1].units[m.toU].sym
    val fromName = if (m.cat == 0) Rates.name(m.fromC) else Converter.sets[m.cat - 1].units[m.fromU].name
    val toName = if (m.cat == 0) Rates.name(m.toC) else Converter.sets[m.cat - 1].units[m.toU].name
    val outText = out?.let { CalcEngine.format(it, 10) } ?: "—"
    val rateLine = if (m.cat == 0) {
        val one = Rates.convert(BigDecimal.ONE, m.fromC, m.toC)
        (if (one != null) "1 ${m.fromC} = ${CalcEngine.format(one, 8)} ${m.toC}   /   " else "") + (if (m.offline && stamp > 0) "OFFLINE, " else "") + ago(stamp)
    } else ""

    @Composable
    fun Chips() {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cats.forEachIndexed { i, n ->
                val on = i == m.cat
                Box(
                    Modifier.height(if (land) 32.dp else 36.dp).clip(CircleShape).background(if (on) pal.tiles[0] else cardBg()).clickable { m.pickCat(i) }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { BasicText(n.uppercase(), style = monoStyle(if (on) pal.ink else pal.onGround.copy(alpha = 0.7f), 10, bold = true), maxLines = 1) }
            }
        }
    }

    @Composable
    fun Cards(mod: Modifier) {
        val ch = if (land) 0.dp else 92.dp
        Column(mod, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ValueCard("FROM", fromLabel, fromName, m.amount.ifEmpty { "0" }, 1f, if (land) Modifier.weight(1f) else Modifier.height(ch), onPick = { onPick(1) }, onLong = { copy(ctx, m.amount) })
            ValueCard("TO", toLabel, toName, outText, 0.75f, if (land) Modifier.weight(1f) else Modifier.height(ch), onPick = { onPick(2) }, onLong = { if (out != null) copy(ctx, CalcEngine.format(out, 15, false)) })
            if (m.cat == 0) {
                if (m.loading) BasicText("GETTING LIVE RATES...", style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9), modifier = Modifier.padding(horizontal = 8.dp))
                else BasicText(rateLine, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 9), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }

    @Composable
    fun Pad(mod: Modifier) {
        Column(mod, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyRow(Modifier.weight(1f)) { Key("7") { m.amountKey("7") }; Key("8") { m.amountKey("8") }; Key("9") { m.amountKey("9") }; Key("", 1, glyph = "backspace") { m.amountKey("⌫") } }
            KeyRow(Modifier.weight(1f)) { Key("4") { m.amountKey("4") }; Key("5") { m.amountKey("5") }; Key("6") { m.amountKey("6") }; Key("AC", 4, 18) { m.amountKey("AC") } }
            KeyRow(Modifier.weight(1f)) { Key("1") { m.amountKey("1") }; Key("2") { m.amountKey("2") }; Key("3") { m.amountKey("3") }; Key("±", 1, 22) { m.amountKey("±") } }
            KeyRow(Modifier.weight(1f)) { Key("0", weight = 2f) { m.amountKey("0") }; Key(".") { m.amountKey(".") }; Key("", 1, glyph = "swap", weight = 1f) { m.swap() } }
        }
    }

    if (land) {
        // Sideways: the two values on the left, the keypad on the right, so the keys are always reachable.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chips()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Cards(Modifier.weight(1f).fillMaxHeight())
                Pad(Modifier.weight(1f).fillMaxHeight())
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chips()
            Cards(Modifier.fillMaxWidth())
            Pad(Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ValueCard(tag: String, unit: String, unitName: String, value: String, strength: Float, size: Modifier, onPick: () -> Unit, onLong: () -> Unit) {
    val pal = LocalPalette.current
    Row(
        size.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if (strength >= 1f) cardBg() else surfaceColor(0.05f)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(112.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onPick).padding(vertical = 6.dp)) {
            BasicText(tag, style = monoStyle(pal.onGround.copy(alpha = 0.5f), 9), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(unit, style = dispStyle(pal.tiles[0], 24), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Glyph("down", pal.onGround.copy(alpha = 0.6f), 16.dp, 2.4f)
            }
            BasicText(unitName, style = bodyStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.weight(1f).fillMaxHeight().pointerInput(value) { detectTapGestures(onLongPress = { onLong() }) }, contentAlignment = Alignment.CenterEnd) {
            FitText(value, pal.onGround, 36, Modifier.fillMaxWidth())
        }
    }
}

// ---- pickers -----------------------------------------------------------------------------------

@Composable
private fun UnitPicker(m: CalcModel, from: Boolean, onClose: () -> Unit) {
    val pal = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val entries: List<Triple<String, String, String>> = if (m.cat == 0) {
        Rates.codes().map { Triple(it, Rates.name(it), it) }
    } else Converter.sets[m.cat - 1].units.mapIndexed { i, u -> Triple(i.toString(), u.name, u.sym) }
    val shown = entries.filter { query.isBlank() || it.second.contains(query, true) || it.third.contains(query, true) }
    val selected = if (m.cat == 0) (if (from) m.fromC else m.toC) else (if (from) m.fromU else m.toU).toString()
    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(surfaceColor(0.1f)).clickable { onClose() }, contentAlignment = Alignment.Center) {
                Glyph("back", pal.onGround, 22.dp, 2.4f)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                BasicText(if (from) "Convert from" else "Convert to", style = dispStyle(pal.onGround, 26), maxLines = 1)
                BasicText(if (m.cat == 0) "${entries.size} CURRENCIES" else Converter.sets[m.cat - 1].name.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10))
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(surfaceColor()).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("search", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
            Spacer(Modifier.width(8.dp))
            Field(query, { query = it }, "Search", Modifier.weight(1f))
        }
        if (entries.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BasicText("Connect to the internet once to load exchange rates.", style = bodyStyle(pal.onGround.copy(alpha = 0.7f), 15), modifier = Modifier.padding(horizontal = 24.dp))
                Pill(if (m.loading) "LOADING..." else "TRY AGAIN", pal.tiles[0], pal.ink) { scope.launch { m.refresh() } }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(shown, key = { it.first }) { e ->
                val on = e.first == selected
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable {
                        if (m.cat == 0) { if (from) m.fromC = e.first else m.toC = e.first }
                        else { val i = e.first.toInt(); if (from) m.fromU = i else m.toU = i }
                        onClose()
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent))
                    Spacer(Modifier.width(12.dp))
                    BasicText(e.second, style = bodyStyle(pal.onGround, 16, medium = on), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(e.third, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 11), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HistoryPage(m: CalcModel, onClose: () -> Unit) {
    val pal = LocalPalette.current
    Column(
        Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleBtn("back") { onClose() }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                BasicText("History", style = dispStyle(pal.onGround, 30), maxLines = 1)
                BasicText(
                    if (m.history.isEmpty()) "NOTHING YET" else "${m.history.size} CALCULATION${if (m.history.size == 1) "" else "S"} / TAP TO USE",
                    style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1,
                )
            }
            if (m.history.isNotEmpty()) CircleBtn("trash") { m.clearHistory() }
        }
        if (m.history.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(surfaceColor(0.1f)), contentAlignment = Alignment.Center) {
                    Glyph("clock", pal.onGround.copy(alpha = 0.6f), 30.dp, 2f)
                }
                BasicText("No history yet", style = dispStyle(pal.onGround, 22), modifier = Modifier.padding(top = 16.dp))
                BasicText(
                    "Finish a calculation with = and it shows up here.",
                    style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 14), modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(m.history.toList()) { (e, r) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardBg())
                            .clickable { m.insertResult(r); onClose() }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            BasicText(e, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText("= $r", style = dispStyle(pal.onGround, 28), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        Glyph("chev", pal.onGround.copy(alpha = 0.35f), 20.dp, 2f)
                    }
                }
            }
        }
    }
}
