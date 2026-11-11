package dev.nncx.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.nncx.devtools.DeepLink
import dev.nncx.devtools.DevToggles
import dev.nncx.devtools.Memory
import dev.nncx.devtools.Toggle
import dev.nncx.theme.LocalNncx
import kotlinx.coroutines.launch

/**
 * The tools only exist with the skin off. In normal mode NNCx has no idea they
 * are there, which is the whole point of the x.
 */

@Composable
fun ToolsHeader(count: Int, open: Boolean, granted: Boolean, onToggle: () -> Unit) {
    val t = LocalNncx.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .clip(RoundedCornerShape(t.corner))
            .pointerInput(Unit) { detectTapGestures { onToggle() } }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(if (open) "⌄" else "›", t.onSurfaceDim, 15.sp, true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt("Tools", t.onSurface, 17.sp, false, FontWeight.Medium)
            Txt(
                if (granted) "secure settings · granted" else "needs WRITE_SECURE_SETTINGS",
                t.onSurfaceDim, 11.sp, true,
            )
        }
        Txt("$count", t.onSurfaceDim, 13.sp, true)
    }
}

@Composable
fun GrantRow() {
    val t = LocalNncx.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures {
                    copy(context, DevToggles.ADB_GRANT)
                    copied = true
                }
            },
    ) {
        Txt(
            if (copied) "copied — run it, then reopen" else "tap to copy the grant",
            t.danger.copy(alpha = 1f), 12.sp, false,
        )
        Spacer(Modifier.height(6.dp))
        Txt(DevToggles.ADB_GRANT, t.onSurfaceDim, 10.sp, true)
    }
}

@Composable
fun ToggleRow(toggle: Toggle, enabled: Boolean) {
    val t = LocalNncx.current
    val context = LocalContext.current
    var on by remember(toggle.id) { mutableStateOf(DevToggles.read(context, toggle)) }
    var failed by remember(toggle.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .pointerInput(toggle.id, enabled) {
                detectTapGestures {
                    if (!enabled) return@detectTapGestures
                    val ok = DevToggles.write(context, toggle, !on)
                    failed = !ok
                    on = DevToggles.read(context, toggle)
                }
            }
            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                toggle.label,
                if (enabled) t.onSurface else t.onSurfaceDim,
                16.sp, false,
            )
            Txt(
                if (failed) "write refused" else toggle.detail,
                if (failed) t.danger.copy(alpha = 1f) else t.onSurfaceDim,
                10.sp, true,
            )
        }
        // Solid when on, outlined when off. No switch chrome.
        Box(
            Modifier
                .size(width = 34.dp, height = 20.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (on) t.accentFull else Color.Transparent)
                .border(1.dp, if (on) t.accentFull else t.hairline, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Txt(if (on) "ON" else "OFF", if (on) t.scrim else t.onSurfaceDim, 9.sp, true, FontWeight.Bold)
        }
    }
}

@Composable
fun DeepLinkRow() {
    val t = LocalNncx.current
    val context = LocalContext.current
    var uri by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    val match = remember(uri) {
        if (uri.isBlank()) null else DeepLink.resolve(context, uri)
    }

    Column(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 10.dp, bottom = 14.dp)) {
        Txt("Deep link", t.onSurface, 16.sp, false)
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.corner))
                .background(t.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = uri,
                onValueChange = { uri = it; result = null },
                singleLine = true,
                textStyle = TextStyle(color = t.onSurface, fontSize = 13.sp, fontFamily = t.dataFamily),
                cursorBrush = SolidColor(t.accentFull),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (uri.isEmpty()) Txt("myapp://order/4821", t.onSurfaceDim, 13.sp, true)
                    inner()
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Txt(
            result ?: match?.let { "→ ${it.label} · ${it.component}" }
                ?: if (uri.isBlank()) "paste a uri — nothing claims it until you type one"
                else "nothing claims this uri",
            if (result != null) t.danger.copy(alpha = 1f) else t.onSurfaceDim,
            10.sp, true,
        )

        if (match != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(t.corner))
                    .background(t.accentFull)
                    .pointerInput(uri) {
                        detectTapGestures { result = DeepLink.fire(context, uri) }
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Txt("fire", t.scrim, 13.sp, true, FontWeight.Bold)
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("nncx", text))
    }
}

@Composable
private fun Txt(
    value: String,
    color: Color,
    size: TextUnit,
    mono: Boolean,
    weight: FontWeight = FontWeight.Normal,
) {
    val t = LocalNncx.current
    BasicText(
        text = value,
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

/**
 * Free background memory.
 *
 * killBackgroundProcesses needs no grant, but it cannot touch foreground apps
 * or live services — so the honest version reports the actual before/after
 * rather than claiming a number.
 */
@Composable
fun MemoryRow() {
    val t = LocalNncx.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .pointerInput(Unit) {
                detectTapGestures {
                    if (busy) return@detectTapGestures
                    busy = true
                    scope.launch {
                        val freed = Memory.free(context)
                        result = if (freed.gainedMb > 0) {
                            "freed ${freed.gainedMb} MB · ${freed.apps} apps asked"
                        } else {
                            "nothing to reclaim · ${freed.apps} apps asked"
                        }
                        busy = false
                    }
                }
            }
            .padding(start = 28.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt("Free memory", t.onSurface, 16.sp, false)
            Txt(
                result ?: if (busy) "killing background processes…" else "killBackgroundProcesses",
                t.onSurfaceDim, 10.sp, true,
            )
        }
    }
}
