package dev.nncx.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import dev.nncx.R
import dev.nncx.contacts.CallHarvester
import dev.nncx.contacts.Contact
import dev.nncx.contacts.ContactIndex
import dev.nncx.index.Kind
import dev.nncx.index.Rank
import dev.nncx.index.UseLog
import dev.nncx.theme.LocalNncx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val GRANTS =
    "adb shell pm grant dev.nncx android.permission.READ_CONTACTS && " +
        "adb shell pm grant dev.nncx android.permission.READ_CALL_LOG && " +
        "adb shell pm grant dev.nncx android.permission.CALL_PHONE"

private data class Ranked(val contact: Contact, val rank: Rank)

/**
 * The dialer, built on the same engine as everything else.
 *
 * A person is a target with `kind = contact`; recurrence is the same 14-day
 * window; the comparator is the one the app list uses. List first, no keypad —
 * if the ranking works you almost never dial digits, and the system dialer is
 * one tap away for the times you do.
 */
@Composable
fun DialerScreen(onClose: () -> Unit) {
    val t = LocalNncx.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var keypad by remember { mutableStateOf(false) }
    var people by remember { mutableStateOf<List<Ranked>>(emptyList()) }
    var granted by remember { mutableStateOf(has(context, Manifest.permission.READ_CONTACTS)) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val today = LocalDate.now().toEpochDay()
        CallHarvester.harvest(context, today)
        val log = UseLog.get(context)
        val fromCalls = withContext(Dispatchers.IO) { log.ranks(Kind.CONTACT, today) }
        val fromTaps = withContext(Dispatchers.IO) { log.ranks(Kind.CONTACT_TAP, today) }
        val loaded = ContactIndex.load(context)

        people = loaded
            .map { c ->
                val rank = (fromCalls[c.key] ?: Rank.NONE) + (fromTaps[c.key] ?: Rank.NONE)
                Ranked(c, rank)
            }
            // Same comparator as the apps: most used is closest.
            .sortedWith(compareByDescending<Ranked> { it.rank.total14 }.thenBy { it.contact.sortKey })
    }

    // One list, two ways in: letters from the search field, digits from the pad.
    val shown = remember(people, query, digits, keypad) {
        if (keypad) {
            val d = digits.filter(Char::isDigit)
            if (d.isBlank()) people
            else people.filter { it.contact.number.filter(Char::isDigit).contains(d) }
        } else {
            val q = query.trim().lowercase()
            if (q.isBlank()) people
            else people.filter {
                it.contact.sortKey.contains(q) || it.contact.number.filter(Char::isDigit).contains(q)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(t.scrim)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(if (keypad) "Dial" else "People", t.onSurface, 22.sp, false, FontWeight.Medium)
                Box(Modifier.weight(1f))
                Box(
                    Modifier.size(40.dp).pointerInput(Unit) {
                        detectTapGestures { keypad = !keypad; digits = ""; query = "" }
                    },
                    contentAlignment = Alignment.Center,
                ) { PadMark(if (keypad) t.accentFull else t.onSurfaceDim) }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(36.dp).pointerInput(Unit) { detectTapGestures { onClose() } },
                    contentAlignment = Alignment.Center,
                ) { Txt("✕", t.onSurfaceDim, 18.sp, true) }
            }

            if (!granted) {
                GrantNotice { granted = has(context, Manifest.permission.READ_CONTACTS) }
                return@Column
            }

            if (!keypad) Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = t.gutter)
                    .clip(RoundedCornerShape(t.corner))
                    .background(t.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = t.onSurface, fontSize = 16.sp,
                        fontFamily = if (t.isXray) t.dataFamily else t.uiFamily,
                    ),
                    cursorBrush = SolidColor(t.accentFull),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Txt("Name or number", t.onSurfaceDim, 16.sp, false)
                        inner()
                    },
                )
            }

            if (keypad) {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = t.gutter, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Txt(digits.ifEmpty { " " }, t.onSurface, 32.sp, true, FontWeight.Light) }
            }

            Spacer(Modifier.height(if (keypad) 2.dp else t.gutter))

            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { "p-" + it.contact.key }) { r ->
                    ContactRow(r) {
                        CallHarvester.record(context, r.contact.key)
                        call(context, r.contact.number)
                    }
                }
            }

            if (keypad) {
                Keypad(
                    onKey = { digits += it },
                    onPlus = { digits += "+" },
                    onBack = { digits = digits.dropLast(1) },
                    onCall = {
                        val n = digits.ifBlank { shown.firstOrNull()?.contact?.number.orEmpty() }
                        if (n.isNotBlank()) {
                            ContactIndex.bucket(n)?.let { CallHarvester.record(context, it) }
                            call(context, n)
                        }
                    },
                )
            }
        }
    }
}

/** 1-9, * 0 #. Letters because a dialpad without them stops looking like one. */
private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
private fun Keypad(
    onKey: (String) -> Unit,
    onPlus: () -> Unit,
    onBack: () -> Unit,
    onCall: () -> Unit,
) {
    val t = LocalNncx.current
    Column(
        Modifier.fillMaxWidth().padding(start = t.gutter, end = t.gutter, bottom = 8.dp)
    ) {
        KEYS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { pair ->
                    val digit = pair.first
                    val letters = pair.second
                    Box(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pointerInput(digit) {
                                detectTapGestures(
                                    onTap = { onKey(digit) },
                                    onLongPress = { if (digit == "0") onPlus() },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Txt(digit, t.onSurface, 25.sp, true, FontWeight.Light)
                            if (letters.isNotEmpty()) Txt(letters, t.onSurfaceDim, 9.sp, true)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(t.corner))
                    .background(t.accentDeep)
                    .pointerInput(Unit) { detectTapGestures { onCall() } },
                contentAlignment = Alignment.Center,
            ) { CallMark(Color.White, 22.dp) }
            Box(
                Modifier
                    .weight(1f)
                    .height(50.dp)
                    .pointerInput(Unit) { detectTapGestures { onBack() } },
                contentAlignment = Alignment.Center,
            ) { Txt("\u232B", t.onSurfaceDim, 20.sp, true) }
        }
    }
}

/** The keypad toggle: nine dots, the pad reduced to its grid. */
@Composable
private fun PadMark(color: Color, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val step = this.size.width / 2f
        val r = this.size.width * 0.085f
        for (row in 0..2) for (col in 0..2) {
            drawCircle(color, r, Offset(col * step, row * step))
        }
    }
}

/** The same handset, where the call actually happens. */
@Composable
private fun CallMark(color: Color, size: Dp) {
    Image(
        painter = painterResource(R.drawable.ic_phone),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size),
    )
}

@Composable
private fun ContactRow(ranked: Ranked, onCall: () -> Unit) {
    val t = LocalNncx.current
    val c = ranked.contact

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = t.rowHeight)
            .pointerInput(c.key) { detectTapGestures { onCall() } }
            .padding(horizontal = t.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, t.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Txt(c.initial, t.onSurfaceDim, 15.sp, false, FontWeight.Medium) }

        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(c.name, t.onSurface, 17.sp, false)
            if (t.xray > 0.05f) Txt(c.number, t.onSurfaceDim, 11.sp, true)
        }
        if (t.xray > 0.05f && ranked.rank.total14 > 0) {
            Txt("${ranked.rank.total14}/${UseLog.WINDOW_DAYS}d", t.onSurfaceDim, 11.sp, true)
        }
    }
}

@Composable
private fun GrantNotice(onRecheck: () -> Unit) {
    val t = LocalNncx.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = t.gutter, vertical = 12.dp)
            .pointerInput(Unit) { detectTapGestures { onRecheck() } }
    ) {
        Txt("Contacts and call log not granted", t.danger.copy(alpha = 1f), 14.sp, false)
        Spacer(Modifier.height(8.dp))
        Txt(GRANTS, t.onSurfaceDim, 10.sp, true)
        Spacer(Modifier.height(8.dp))
        Txt("run it, then tap here", t.onSurfaceDim, 11.sp, false)
    }
}

private fun has(context: Context, permission: String) =
    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

/**
 * NNCx does not become the default dialer. ROLE_DIALER means building the whole
 * in-call UI, and a crash there drops a live call — not a toy's job. Fire the
 * intent, let the system keep the dangerous part.
 */
private fun call(context: Context, number: String) {
    val uri = Uri.fromParts("tel", number, null)
    val direct = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val dial = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (has(context, Manifest.permission.CALL_PHONE)) {
        runCatching { context.startActivity(direct) }.onFailure {
            runCatching { context.startActivity(dial) }
        }
    } else {
        runCatching { context.startActivity(dial) }
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
            color = color, fontSize = size,
            fontFamily = if (mono) t.dataFamily else t.uiFamily,
            fontWeight = weight,
        ),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
