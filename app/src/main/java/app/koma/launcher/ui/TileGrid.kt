package app.koma.launcher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.koma.launcher.data.Tile

class Cell(val col: Int, val row: Int)

/** First-fit packing: each tile takes the first free spot, scanning rows top to bottom. */
fun pack(tiles: List<Tile>, columns: Int): List<Cell> {
    val occupied = ArrayList<BooleanArray>()
    fun ensureRows(count: Int) {
        while (occupied.size < count) occupied.add(BooleanArray(columns))
    }

    return tiles.map { t ->
        val w = minOf(t.w, columns)
        var row = 0
        var col = -1
        while (col < 0) {
            ensureRows(row + t.h)
            for (c in 0..columns - w) {
                var free = true
                for (dr in 0 until t.h) for (dc in 0 until w) if (occupied[row + dr][c + dc]) free = false
                if (free) {
                    col = c
                    break
                }
            }
            if (col < 0) row++
        }
        for (dr in 0 until t.h) for (dc in 0 until w) occupied[row + dr][col + dc] = true
        Cell(col, row)
    }
}

/**
 * A grid of square cells. Every tile is measured at an exact size, so there is no
 * intrinsic measuring and the layout pass stays cheap even with many tiles.
 */
@Composable
fun TileGrid(
    tiles: List<Tile>,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    cellHeightPx: Int = 0,
    gap: Dp = 6.dp,
    tileModifier: (Tile) -> Modifier = { Modifier },
    content: @Composable (Tile) -> Unit,
) {
    val cells = remember(tiles, columns) { pack(tiles, columns) }
    Layout(
        content = { tiles.forEach { t -> key(t.id) { Box(tileModifier(t)) { content(t) } } } },
        modifier = modifier,
    ) { measurables, constraints ->
        val g = gap.roundToPx()
        val cell = (constraints.maxWidth - g * (columns - 1)) / columns
        // Cells are square unless a row height was given (rows per screen setting).
        val ch = if (cellHeightPx > 0) cellHeightPx else cell
        val placeables = measurables.mapIndexed { i, m ->
            val t = tiles[i]
            val w = minOf(t.w, columns)
            m.measure(Constraints.fixed(w * cell + (w - 1) * g, t.h * ch + (t.h - 1) * g))
        }
        var rows = 0
        for (i in tiles.indices) rows = maxOf(rows, cells[i].row + tiles[i].h)
        val height = if (rows == 0) 0 else rows * ch + (rows - 1) * g
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { i, p ->
                p.place(cells[i].col * (cell + g), cells[i].row * (ch + g))
            }
        }
    }
}
