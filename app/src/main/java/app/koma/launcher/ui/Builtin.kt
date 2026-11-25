package app.koma.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ---- focus lock --------------------------------------------------------------------------------

internal val dayNames = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
internal fun AppChecklist(state: HomeState, selected: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier, max: Int = Int.MAX_VALUE) {
    val pal = state.palette
    var query by remember { mutableStateOf("") }
    val list = state.apps.distinctBy { it.pkg }.filter { query.isBlank() || it.label.contains(query, true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(surfaceColor()).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("search", pal.onGround.copy(alpha = 0.6f), 18.dp, 2f)
            Spacer(Modifier.width(8.dp))
            Field(query, { query = it }, "Search apps", Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.key }) { a ->
                val on = a.pkg in selected
                val full = !on && selected.size >= max
                val toggle = { if (on) onChange(selected - a.pkg) else if (!full) onChange(selected + a.pkg) }
                Row(
                    Modifier.fillMaxWidth().height(48.dp).alpha(if (full) 0.35f else 1f).clickable { toggle() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(state.icons, a, 34.dp, bg = pal.tiles[0], ink = pal.ink, line = pal.onGround)
                    BasicText(a.label, style = bodyStyle(pal.onGround, 16), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CheckBox(on, pal.tiles[0], pal.ink) { toggle() }
                }
            }
        }
    }
}
