package dev.nncx.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nncx.folders.AppFacts
import dev.nncx.lock.VitalsService
import dev.nncx.settings.Settings
import dev.nncx.theme.Accent
import dev.nncx.theme.LocalNncx

private val Presets = listOf(
    "#502B5D", "#1E3A5F", "#0F4C45", "#1F4D2E", "#6B4518", "#6B2420",
)

@Composable
fun SettingsScreen(all: List<AppFacts>, onClose: () -> Unit) {
    val t = LocalNncx.current
    val context = LocalContext.current
    val settings = Settings.get(context)
    var picking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(t.scrim)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        ) {
            Header(if (picking) "Pin an app" else "Settings") {
                if (picking) picking = false else onClose()
            }
            Rule()

            if (picking) {
                AppPicker(all, settings.pins) { pkg ->
                    if (pkg !in settings.pins) settings.pins = settings.pins + pkg
                    picking = false
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    item { Section("Accent") }
                    item { Swatches(settings.accent) { settings.accent = it } }
                    item { HexField(settings.accent) { settings.accent = it } }
                    item { Rule() }

                    item { Section("Dock") }
                    item {
                        Choice("Slots", listOf("4", "5", "6", "7"), settings.dockSize.toString()) {
                            settings.dockSize = it.toInt()
                        }
                    }
                    item {
                        Choice(
                            "Rail side",
                            listOf("Left", "Right"),
                            if (settings.dockOnRight) "Right" else "Left",
                        ) { settings.dockOnRight = it == "Right" }
                    }
                    item {
                        Choice(
                            "Folded dock",
                            listOf("Bottom", "Side"),
                            if (settings.coverRail) "Side" else "Bottom",
                        ) { settings.coverRail = it == "Side" }
                    }
                    item { Rule() }

                    item { Section("Pinned · ${settings.pins.size}") }
                    itemsIndexed(settings.pins, key = { _, p -> "pin-$p" }) { i, pkg ->
                        PinRow(
                            pkg = pkg,
                            all = all,
                            canUp = i > 0,
                            canDown = i < settings.pins.lastIndex,
                            onUp = { settings.pins = settings.pins.swap(i, i - 1) },
                            onDown = { settings.pins = settings.pins.swap(i, i + 1) },
                            onRemove = { settings.pins = settings.pins - pkg },
                        )
                    }
                    item {
                        Tappable({ picking = true }) {
                            Txt("Add a pin", t.accentFull, 16.sp, false, FontWeight.Medium)
                        }
                    }
                    item { Rule() }

                    item { Section("Lock screen") }
                    item {
                        Choice(
                            "Vitals readout",
                            listOf("Off", "On"),
                            if (settings.vitalsOnLock) "On" else "Off",
                        ) {
                            settings.vitalsOnLock = it == "On"
                            VitalsService.sync(context)
                        }
                    }
                    item {
                        Box(Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 4.dp)) {
                            Txt(
                                "an ongoing notification, in your hex — the only lock screen Android gives away",
                                t.onSurfaceDim, 11.sp, false,
                            )
                        }
                    }
                    item { Rule() }

                    item { Section("Work packages") }
                    item {
                        PrefixField(settings.workPrefixes) { settings.workPrefixes = it }
                    }
                    item { Rule() }

                    item {
                        Tappable({ settings.resetToBuildDefaults() }) {
                            Txt("Reset to build defaults", t.danger.copy(alpha = 1f), 15.sp, false)
                        }
                    }
                    item { Spacer(Modifier.height(32.dp)) }
                }
            }
        }
    }
}

// ---- pieces ---------------------------------------------------------------

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(title, t.onSurface, 22.sp, false, FontWeight.Medium)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f))
        Box(
            Modifier
                .size(36.dp)
                .pointerInput(Unit) { detectTapGestures { onBack() } },
            contentAlignment = Alignment.Center,
        ) { Txt("✕", t.onSurfaceDim, 18.sp, true) }
    }
}

@Composable
private fun Section(label: String) {
    val t = LocalNncx.current
    Box(Modifier.fillMaxWidth().padding(start = t.gutter, top = 24.dp, bottom = 8.dp)) {
        Txt(label.uppercase(), t.onSurfaceDim, 11.sp, true)
    }
}

@Composable
private fun Rule() {
    val t = LocalNncx.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(t.hairline))
}

@Composable
private fun Tappable(onClick: () -> Unit, content: @Composable () -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = t.gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Swatches(current: String, onPick: (String) -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Presets.forEach { hex ->
            // Show the NORMALISED colour, so the swatch is what you actually get.
            val shown = remember(hex) { Accent.ground(hex) }
            val selected = hex.equals(current, ignoreCase = true)
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(shown)
                    .border(
                        if (selected) 3.dp else 1.dp,
                        if (selected) t.onSurface else t.hairline,
                        CircleShape,
                    )
                    .pointerInput(hex) { detectTapGestures { onPick(hex) } }
            )
        }
    }
}

@Composable
private fun HexField(current: String, onSet: (String) -> Unit) {
    val t = LocalNncx.current
    var text by remember(current) { mutableStateOf(current) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(6.dp))
                .background(remember(current) { Accent.normalize(current) })
        )
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                if (it.trim().removePrefix("#").length == 6) onSet(it.trim())
            },
            singleLine = true,
            textStyle = TextStyle(
                color = t.onSurface, fontSize = 15.sp, fontFamily = t.dataFamily,
            ),
            cursorBrush = SolidColor(t.onSurface),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Choice(
    label: String,
    options: List<String>,
    selected: String,
    onPick: (String) -> Unit,
) {
    val t = LocalNncx.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(label, t.onSurface, 16.sp, false)
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val on = option == selected
                Box(
                    Modifier
                        .clip(RoundedCornerShape(t.corner))
                        .background(if (on) t.onSurface else Color.Transparent)
                        .border(1.dp, if (on) t.onSurface else t.hairline, RoundedCornerShape(t.corner))
                        .pointerInput(option) { detectTapGestures { onPick(option) } }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Txt(option, if (on) t.scrim else t.onSurfaceDim, 14.sp, false)
                }
            }
        }
    }
}

@Composable
private fun PinRow(
    pkg: String,
    all: List<AppFacts>,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val t = LocalNncx.current
    val facts = remember(pkg, all) { all.firstOrNull { it.packageName == pkg } }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(horizontal = t.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (facts != null) {
            AppIconView(facts.entry, 32.dp)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Txt(facts?.entry?.label ?: pkg, t.onSurface, 16.sp, false)
            Txt(pkg, t.onSurfaceDim, 11.sp, true)
        }
        // Arrows rather than drag: a long-press drag inside a scrolling list
        // fights the scroll, and the dock is six slots, not sixty.
        Glyph("\u2191", canUp, onUp)
        Glyph("\u2193", canDown, onDown)
        Glyph("\u2715", true, onRemove)
    }
}

@Composable
private fun Glyph(mark: String, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalNncx.current
    Box(
        Modifier
            .size(36.dp)
            .pointerInput(mark, enabled) {
                detectTapGestures { if (enabled) onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        Txt(
            mark,
            if (enabled) t.onSurfaceDim else t.onSurfaceDim.copy(alpha = 0.25f),
            15.sp, true,
        )
    }
}

/** Pin order is dock order, so moving a row moves the slot. */
private fun List<String>.swap(a: Int, b: Int): List<String> =
    toMutableList().also {
        val tmp = it[a]; it[a] = it[b]; it[b] = tmp
    }

@Composable
private fun PrefixField(current: List<String>, onSet: (List<String>) -> Unit) {
    val t = LocalNncx.current
    var text by remember(current) { mutableStateOf(current.joinToString(", ")) }
    Box(Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 10.dp)) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onSet(it.split(",").map { p -> p.trim() }.filter { p -> p.isNotEmpty() })
            },
            textStyle = TextStyle(
                color = t.onSurface, fontSize = 14.sp, fontFamily = t.dataFamily,
            ),
            cursorBrush = SolidColor(t.onSurface),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppPicker(
    all: List<AppFacts>,
    pinned: List<String>,
    onPick: (String) -> Unit,
) {
    val t = LocalNncx.current
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 12.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = t.onSurface, fontSize = 16.sp, fontFamily = t.uiFamily,
                ),
                cursorBrush = SolidColor(t.onSurface),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (query.isEmpty()) Txt("Search", t.onSurfaceDim, 16.sp, false)
                    inner()
                },
            )
        }
        Rule()
        val shown = remember(all, query) {
            val q = query.trim().lowercase()
            all.filter { q.isBlank() || it.entry.sortKey.contains(q) || it.packageName.contains(q) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { "pick-" + it.packageName }) { f ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .pointerInput(f.packageName) {
                            detectTapGestures { onPick(f.packageName) }
                        }
                        .padding(horizontal = t.gutter, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconView(f.entry, 32.dp)
                    Spacer(Modifier.width(14.dp))
                    Txt(f.entry.label, t.onSurface, 16.sp, false, modifier = Modifier.weight(1f))
                    if (f.packageName in pinned) Txt("pinned", t.onSurfaceDim, 11.sp, true)
                }
            }
        }
    }
}

@Composable
private fun Txt(
    value: String,
    color: Color,
    size: TextUnit,
    mono: Boolean,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    val t = LocalNncx.current
    BasicText(
        text = value,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = if (mono) t.dataFamily else t.uiFamily,
            fontWeight = weight,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
