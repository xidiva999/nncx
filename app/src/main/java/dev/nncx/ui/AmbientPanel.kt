package dev.nncx.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nncx.devtools.Perf
import dev.nncx.theme.LocalNncx
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The left half of the open screen.
 *
 * A square canvas gives you room a phone never had, and a column of folder
 * names was not using it. So: the time while it is a phone, the machine's own
 * vitals once it is an instrument. Both are composed at once and cross-faded
 * by the same float, so dragging the x dissolves one into the other.
 */
@Composable
fun AmbientPanel(modifier: Modifier = Modifier) {
    val t = LocalNncx.current
    val context = LocalContext.current

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // Wake on the minute boundary rather than every ten seconds, so the clock
    // never sits a minute behind the status bar and nothing spins in between.
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalDateTime.now()
            now = t
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000L))
        }
    }

    val live = t.xray > 0.05f
    var perf by remember { mutableStateOf<Perf?>(null) }
    LaunchedEffect(live) {
        while (live) {
            perf = Perf.sample(context)
            delay(2_000)
        }
    }

    Box(modifier.fillMaxSize().padding(start = 4.dp, top = 24.dp, end = 20.dp)) {
        if (t.xray < 0.98f) ClockFace(now, 1f - t.xray)
        if (t.xray > 0.02f) PerfFace(perf, t.xray)
    }
}

@Composable
private fun ClockFace(now: LocalDateTime, alpha: Float) {
    val t = LocalNncx.current
    val context = LocalContext.current
    val pattern = remember(context) { if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm" }
    val time = remember(now, pattern) { now.format(DateTimeFormatter.ofPattern(pattern)) }
    val day = remember(now) { now.format(DateTimeFormatter.ofPattern("EEEE")) }
    val date = remember(now) { now.format(DateTimeFormatter.ofPattern("d MMMM")) }

    Column {
        Txt(time, t.onSurface.copy(alpha = alpha), 76.sp, false, FontWeight.Light)
        Spacer(Modifier.height(10.dp))
        Txt(day, t.onSurface.copy(alpha = alpha), 20.sp, false, FontWeight.Medium)
        Txt(date, t.onSurfaceDim.copy(alpha = alpha), 18.sp, false)
    }
}

@Composable
private fun PerfFace(perf: Perf?, alpha: Float) {
    val t = LocalNncx.current
    if (perf == null) return
    val dim = t.onSurfaceDim.copy(alpha = alpha)
    val on = t.onSurface.copy(alpha = alpha)

    Column {
        Txt("vitals", dim, 11.sp, true)
        Spacer(Modifier.height(14.dp))
        Line("uptime", perf.uptime, dim, on)
        Line("memory", "%.1f / %.1f GB".format(perf.memUsedMb / 1024f, perf.memTotalMb / 1024f), dim, on)
        Line("battery", "${perf.batteryPct}%  ${"%.1f".format(perf.batteryTempC)}°C", dim, on)
        Line("draw", "${perf.currentMa} mA", dim, on)
        Line("thermal", perf.thermal, dim, on)
        Line("storage", "%.0f / %.0f GB".format(perf.storageFreeGb, perf.storageTotalGb), dim, on)
    }
}

@Composable
private fun Line(label: String, value: String, dim: Color, on: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Box(Modifier.width(76.dp)) { Txt(label, dim, 12.sp, true) }
        Txt(value, on, 12.sp, true)
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
        maxLines = 1,
    )
}
