package app.koma.launcher.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// ---- text styles -------------------------------------------------------------------------------

fun monoStyle(color: Color, size: Int = 11, bold: Boolean = false) = TextStyle(
    fontFamily = Fonts.Label,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
    fontSize = size.sp,
    letterSpacing = 0.12.em,
    color = color,
)

fun dispStyle(color: Color, size: Int, tracking: Float = -0.02f) = TextStyle(
    fontFamily = Fonts.Display,
    fontWeight = FontWeight.Normal,
    fontSynthesis = FontSynthesis.None,
    fontSize = size.sp,
    letterSpacing = tracking.em,
    color = color,
)

fun bodyStyle(color: Color, size: Int = 16, medium: Boolean = false) = TextStyle(
    fontFamily = Fonts.Body,
    fontWeight = if (medium) FontWeight.Medium else FontWeight.Normal,
    fontSize = size.sp,
    color = color,
)

/** The same colour with no transparency. Used where a tile's own colour is reused as text or glyph colour. */
fun Color.opaque(): Color = copy(alpha = 1f)

/** A surface a little lighter (or darker) than the ground, for cards and sheets. */
@Composable
fun surfaceColor(amount: Float = 0.08f): Color {
    val pal = LocalPalette.current
    return lerp(pal.ground, pal.onGround, amount)
}

// ---- controls ----------------------------------------------------------------------------------

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, height: Int = 38, onClick: () -> Unit) {
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = monoStyle(fg, 11, bold = true), maxLines = 1)
    }
}

@Composable
fun KToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPalette.current
    // The same squared switch the Alarms tile uses.
    Box(
        Modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (on) pal.tiles[0] else pal.onGround.copy(alpha = 0.18f))
            .clickable { onChange(!on) }
            .padding(4.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(7.dp)).background(if (on) pal.ink else pal.onGround.copy(alpha = 0.8f)))
    }
}

/** A checkbox: an empty outline, or a filled square with a tick. */
@Composable
fun KCheck(on: Boolean) {
    val pal = LocalPalette.current
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier.size(24.dp).clip(shape)
            .background(if (on) pal.tiles[0] else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(2.dp, pal.onGround.copy(alpha = 0.5f), shape)),
        contentAlignment = Alignment.Center,
    ) { if (on) Glyph("check", pal.ink, 16.dp, 3.2f) }
}

@Composable
fun SectionLabel(text: String) {
    val pal = LocalPalette.current
    BasicText(text, style = monoStyle(pal.onGround.copy(alpha = 0.6f), 10), modifier = Modifier.padding(top = 10.dp, start = 2.dp))
}

/** A card row: optional coloured glyph square, title, subtitle, and a trailing control. */
@Composable
fun ListRow(
    title: String,
    sub: String = "",
    glyph: String? = null,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val pal = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .card()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, 34.dp, tint ?: pal.tiles[0], pal.ink, pal.onGround)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = bodyStyle(pal.onGround, 16, medium = true))
            // Long explanations are left out; short ones are values (a count, the current choice).
            if (sub.isNotEmpty() && sub.length <= 32) BasicText(sub, style = bodyStyle(pal.onGround.copy(alpha = 0.6f), 13), modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        } else if (onClick != null) {
            Glyph("chev", pal.onGround.copy(alpha = 0.4f), 20.dp, 2f)
        }
    }
}

@Composable
fun SwitchRow(title: String, sub: String = "", on: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, sub, onClick = { onChange(!on) }, trailing = { KToggle(on, onChange) })
}

/** True inside a [Group]: rows drop their own card and sit flat in the group's card. */
val LocalFlat = androidx.compose.runtime.compositionLocalOf { false }

/** The standalone card look for a row. Inside a [Group] it does nothing, because the group is the card. */
@Composable
fun Modifier.card(radius: androidx.compose.ui.unit.Dp = 18.dp): Modifier =
    if (LocalFlat.current) this else this.clip(RoundedCornerShape(radius)).background(surfaceColor())

/**
 * One card holding several rows with a hairline between them, under an optional small title and above an optional hint.
 * ListRow, SwitchRow and Dropdown go flat inside it; anything else should bring its own padding.
 */
@Composable
fun Group(title: String? = null, hint: String? = null, content: @Composable () -> Unit) {
    val pal = LocalPalette.current
    val line = pal.onGround.copy(alpha = 0.1f)
    val ys = remember { ArrayList<Float>() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (title != null) BasicText(title, style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10, bold = true), modifier = Modifier.padding(start = 6.dp, top = 10.dp))
        androidx.compose.runtime.CompositionLocalProvider(LocalFlat provides true) {
            androidx.compose.ui.layout.Layout(
                content = content,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(surfaceColor())
                    .androidx_drawLines(ys, line),
            ) { measurables, c ->
                val placeables = measurables.map { it.measure(c.copy(minHeight = 0)) }
                ys.clear()
                var y = 0
                placeables.forEachIndexed { i, p ->
                    if (i > 0) ys.add(y.toFloat())
                    y += p.height
                }
                layout(c.maxWidth, y) {
                    var at = 0
                    placeables.forEach {
                        it.placeRelative(0, at)
                        at += it.height
                    }
                }
            }
        }
    }
}

private fun Modifier.androidx_drawLines(ys: List<Float>, color: Color): Modifier = this.drawBehind {
    val inset = 14.dp.toPx()
    ys.forEach { y -> drawLine(color, androidx.compose.ui.geometry.Offset(inset, y), androidx.compose.ui.geometry.Offset(size.width - inset, y), 1.dp.toPx()) }
}

/** Padding for loose content (text, buttons, previews) placed inside a [Group]. */
@Composable
fun GroupPad(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/**
 * A choice shown as one row: the current value and a chevron. Tapping opens the options right below it;
 * picking one closes it again. Replaces rows of chips.
 */
@Composable
fun Dropdown(options: List<Pair<String, String>>, selected: String, title: String? = null, placeholder: String = "", onSelect: (String) -> Unit) {
    val pal = LocalPalette.current
    var open by remember { androidx.compose.runtime.mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: placeholder
    Column(Modifier.fillMaxWidth().card().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title != null) {
                BasicText(title, style = bodyStyle(pal.onGround, 16, medium = true), modifier = Modifier.weight(1f))
                BasicText(current, style = monoStyle(pal.onGround.copy(alpha = 0.7f), 11, bold = true), maxLines = 1)
            } else {
                BasicText(current, style = monoStyle(pal.onGround, 12, bold = true), modifier = Modifier.weight(1f), maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.graphicsLayer { rotationZ = if (open) -90f else 90f }) { Glyph("chev", pal.onGround.copy(alpha = 0.55f), 20.dp, 2f) }
        }
        if (open) {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                options.forEach { (id, label) ->
                    val on = id == selected
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) pal.tiles[0].copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                onSelect(id)
                                open = false
                            }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(label, style = monoStyle(if (on) pal.onGround else pal.onGround.copy(alpha = 0.75f), 11, bold = on), modifier = Modifier.weight(1f))
                        if (on) Glyph("check", pal.tiles[0], 18.dp, 3f)
                    }
                }
            }
        }
    }
}

/** Kept for the many call sites that used chips: now a dropdown. */
@Composable
fun ChipRow(options: List<Pair<String, String>>, selected: String, title: String? = null, onSelect: (String) -> Unit) {
    Dropdown(options, selected, title, onSelect = onSelect)
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    style: TextStyle? = null,
    onDone: (() -> Unit)? = null,
    keepKeyboard: Boolean = false,
) {
    val pal = LocalPalette.current
    val st = style ?: bodyStyle(pal.onGround, 16)
    @Suppress("DEPRECATION")
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = st,
        cursorBrush = SolidColor(pal.tiles[0]),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = when {
                onDone == null -> androidx.compose.ui.text.input.ImeAction.Default
                keepKeyboard -> androidx.compose.ui.text.input.ImeAction.Next
                else -> androidx.compose.ui.text.input.ImeAction.Done
            },
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }, onNext = { onDone?.invoke() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) BasicText(hint, style = st.copy(color = pal.onGround.copy(alpha = 0.4f)))
                inner()
            }
        },
    )
}

// ---- page and sheet ----------------------------------------------------------------------------

/** A full-screen page on the ground colour with a back arrow and title. */
@Composable
fun Page(
    title: String,
    kicker: String = "",
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(pal.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) CircleBtn("back", onClick = onBack)
            Column(Modifier.weight(1f).padding(start = if (onBack != null) 14.dp else 4.dp)) {
                BasicText(title, style = dispStyle(pal.onGround, 30), maxLines = 1)
                if (kicker.isNotEmpty()) BasicText(kicker.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
            }
            actions()
        }
        val base = Modifier.weight(1f).fillMaxWidth()
        Column(
            (if (scroll) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** A tile-coloured card with two soft shapes in the theme's other colours. The header card of Settings pages. */
@Composable
fun HeroBox(colour: Color = LocalPalette.current.tiles[0], content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    val others = pal.tiles.filter { it != colour }.ifEmpty { pal.tiles }
    Box(Modifier.fillMaxWidth().clip(TileShape).background(colour)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 46.dp, y = (-56).dp).size(170.dp).clip(CircleShape).background(others[0].copy(alpha = 0.55f)))
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 74.dp).size(54.dp).clip(CircleShape).background(others[1 % others.size].copy(alpha = 0.6f)))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/**
 * The card at the top of a page: a small caption, a big line, an optional note and optional chips. [leading] puts a
 * small picture on the left, with the text beside it.
 */
@Composable
fun PageHero(
    caption: String,
    title: String,
    note: String = "",
    colour: Color = LocalPalette.current.tiles[0],
    chips: List<String> = emptyList(),
    leading: (@Composable () -> Unit)? = null,
) {
    val pal = LocalPalette.current
    val text: @Composable ColumnScope.() -> Unit = {
        if (caption.isNotEmpty()) BasicText(caption.uppercase(), style = monoStyle(pal.ink.copy(alpha = 0.7f), 10, bold = true), maxLines = 1)
        BasicText(title, style = dispStyle(pal.ink, 30), maxLines = 2)
        if (note.isNotEmpty()) BasicText(note, style = bodyStyle(pal.ink.copy(alpha = 0.75f), 13))
        if (chips.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { c ->
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(pal.ink.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    BasicText(c.uppercase(), style = monoStyle(pal.ink, 9, bold = true), maxLines = 1)
                }
            }
        }
    }
    HeroBox(colour) {
        if (leading == null) text() else Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = text)
        }
    }
}

/** One row: the word "Colour" and the wallpaper colours as swatches. [selected] is a palette index, or -1 for automatic (A). */
@Composable
fun ColourRow(selected: Int, label: String = "Colour", onPick: (Int) -> Unit) {
    val pal = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, style = bodyStyle(pal.onGround, 16, medium = true), modifier = Modifier.weight(1f).padding(end = 8.dp))
        Row(Modifier.weight(2f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            (listOf(-1) + pal.all.indices.toList()).forEach { c ->
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(if (c < 0) pal.onGround.copy(alpha = 0.2f) else pal.all[c])
                        .then(if (selected == c) Modifier.border(2.5.dp, pal.onGround, CircleShape) else Modifier)
                        .clickable { onPick(c) },
                    contentAlignment = Alignment.Center,
                ) { if (c < 0) BasicText("A", style = monoStyle(pal.onGround, 10, bold = true)) }
            }
        }
    }
}

/** The app-wide indication: nothing. Taps never draw a ripple or a grey box; only [softPress] animates. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
object NoIndication : androidx.compose.foundation.IndicationNodeFactory {
    override fun create(interactionSource: androidx.compose.foundation.interaction.InteractionSource): androidx.compose.ui.node.DelegatableNode =
        object : androidx.compose.ui.Modifier.Node() {}
    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

/** A tap target with no ripple: it dips a little and washes with [tint] (at [strength]) while held, fading in and out. */
@Composable
fun Modifier.softPress(tint: Color, shape: Shape = RoundedCornerShape(12.dp), strength: Float = 0.08f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val k by animateFloatAsState(if (down) 0.98f else 1f, label = "press")
    val wash by animateColorAsState(tint.copy(alpha = if (down) strength else 0f), label = "wash")
    return graphicsLayer { scaleX = k; scaleY = k }
        .clip(shape).background(wash)
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** A round 48dp button for page headers. [accent] fills it with the main tile colour. */
@Composable
fun CircleBtn(glyph: String, accent: Boolean = false, onClick: () -> Unit) {
    val pal = LocalPalette.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(if (accent) pal.tiles[0] else surfaceColor(0.1f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(glyph, if (accent) pal.ink else pal.onGround, 22.dp, if (accent) 2.6f else 2.2f) }
}

/** The pill of tabs along the bottom, as in Clock: icons only, with the chosen one opened out to show its name. Sideways it becomes a rail down the left edge. */
@Composable
fun PillNav(items: List<Pair<String, String>>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val pal = LocalPalette.current
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    if (land) {
        Column(
            modifier.padding(vertical = 8.dp).width(64.dp).fillMaxHeight().clip(RoundedCornerShape(32.dp)).background(surfaceColor(0.1f)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items.forEachIndexed { i, (glyph, _) ->
                val on = i == selected
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (on) pal.tiles[0] else Color.Transparent).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Glyph(glyph, if (on) pal.ink else pal.onGround.copy(alpha = 0.55f), 22.dp, 1.8f) }
            }
        }
        return
    }
    Row(
        modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp).clip(CircleShape).background(surfaceColor(0.1f)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { i, (glyph, name) ->
            val on = i == selected
            Row(
                Modifier.weight(if (on) 2.3f else 1f).height(52.dp).clip(CircleShape)
                    .background(if (on) pal.tiles[0] else Color.Transparent).clickable { onSelect(i) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
            ) {
                Glyph(glyph, if (on) pal.ink else pal.onGround.copy(alpha = 0.55f), 22.dp, 1.8f)
                if (on) {
                    Spacer(Modifier.width(7.dp))
                    BasicText(name, style = monoStyle(pal.ink, 9, bold = true), maxLines = 1)
                }
            }
        }
    }
}

/** A screen laid out like Clock: round back button, a title with a small caption, buttons on the right, and an optional nav (a bottom pill, or a rail on the left when sideways). */
@Composable
fun AppPage(
    title: String,
    sub: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    nav: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalPalette.current
    val land = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    Row(Modifier.fillMaxSize().background(pal.ground).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 12.dp)) {
        if (land && nav != null) {
            nav()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleBtn("back", onClick = onBack)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    BasicText(title, style = dispStyle(pal.onGround, 30), maxLines = 1)
                    if (sub.isNotBlank()) BasicText(sub.uppercase(), style = monoStyle(pal.onGround.copy(alpha = 0.55f), 10), maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
            if (!land) nav?.invoke()
        }
    }
}

/** A screen with a large title in capitals, a back button and room for something on the right. The body does not scroll by itself. */
@Composable
fun BigPage(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val pal = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(pal.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 18.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A big touch area (56dp), set in from the screen edge where the system's back gesture can swallow taps.
            Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Glyph("back", pal.onGround, 24.dp, 2.2f)
            }
            BasicText(title.uppercase(), style = dispStyle(pal.onGround, 34), maxLines = 1, modifier = Modifier.weight(1f).padding(start = 6.dp))
            trailing()
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * A bottom sheet over a dimmed screen. Tapping outside closes it.
 * [header] stays fixed at the top while the content below it scrolls.
 */
@Composable
fun Sheet(onDismiss: () -> Unit, header: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    val none = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = none, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(shape)
                .background(surfaceColor(0.1f))
                .border(1.dp, pal.onGround.copy(alpha = 0.12f), shape)
                .clickable(interactionSource = none, indication = null, onClick = {})
                .heightIn(max = 640.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(pal.onGround.copy(alpha = 0.25f)))
            if (header != null) {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = header)
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = if (header != null) 6.dp else 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}
