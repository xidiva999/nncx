package dev.nncx.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import dev.nncx.R
import dev.nncx.theme.LocalNncx
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.nncx.folders.AppFacts

/**
 * The dock is the Pinned layer.
 *
 * Everything else in NNCx is derived from use; this is the one place that is
 * intent. It is also what answers the things pure frequency gets wrong —
 * emergencies, importance, and the app you compulsively open but would rather
 * keep further away.
 *
 * The dock is the same in both modes, on purpose. It briefly swapped to My
 * Builds under x-ray, which was wrong twice over: it replaced rather than
 * revealed — the opposite of what the x is for — and it did it to the one part
 * of NNCx that is pure intent and is supposed never to move. Pulling the skin
 * off does not change what you need within reach, and My Builds is a folder:
 * already one tap away.
 */
fun dockItems(
    all: List<AppFacts>,
    pins: List<String>,
    size: Int,
): List<AppFacts> {
    val n = size.coerceIn(3, 8)
    val byPackage = all.associateBy { it.packageName }
    val pinned = pins.mapNotNull { byPackage[it] }
    // Short pin list? Fill the rest from the top of the ranking rather than
    // leaving holes.
    val filler = all.filter { it !in pinned }
    return (pinned + filler).take(n)
}

/**
 * Tap-only, on purpose. Android's back gesture lives in the edge strip on both
 * sides, and setSystemGestureExclusionRects is capped at 200dp per edge — not
 * enough for a six-slot rail. Taps do not conflict with edge gestures; drags
 * do. So the dock is ordered in Settings, never by dragging on it.
 */
@Composable
fun Dock(
    items: List<AppFacts>,
    vertical: Boolean,
    /** Leading slots that are yours by intent; the rest are ranking filler. */
    pinned: Int = 0,
    modifier: Modifier = Modifier,
    onLaunch: (AppFacts) -> Unit,
    /** NNCx's own dialer, given the leading slot rather than a bar of its own. */
    onDialer: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val tile: Dp = if (vertical) 52.dp else 44.dp
    val gap: Dp = if (vertical) 12.dp else 8.dp

    if (vertical) {
        Column(
            modifier,
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            onDialer?.let { DialTile(tile, it) }
            items.forEachIndexed { i, f -> Slot(f, tile, i < pinned, onLaunch) }
            trailing()
        }
    } else {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onDialer?.let { DialTile(tile, it) }
            items.forEachIndexed { i, f -> Slot(f, tile, i < pinned, onLaunch) }
            trailing()
        }
    }
}

@Composable
private fun Slot(facts: AppFacts, size: Dp, pinned: Boolean, onLaunch: (AppFacts) -> Unit) {
    AppIconView(
        entry = facts.entry,
        size = size,
        modifier = Modifier.pointerInput(facts.packageName) {
            detectTapGestures { onLaunch(facts) }
        },
    )
}

/**
 * The dialer's slot. Outlined rather than filled, and in the accent, so it
 * reads as NNCx's own thing sitting among your apps rather than another one
 * of them.
 */
@Composable
private fun DialTile(size: Dp, onClick: () -> Unit) {
    val t = LocalNncx.current
    // Round in both modes. The corner token lerps 28dp -> 3dp across the fold,
    // which is right for cards and rows but turns a 44dp tile from a circle
    // into a square. This is a button, not a card — and it matches the contact
    // avatars in the dialer it opens.
    val shape = CircleShape

    // Inverts across the fold, exactly like the x band. Plum on paper is a
    // mark; plum on near-black is 1.74:1 and effectively gone — so past the
    // halfway point the colour becomes the ground and the mark goes light.
    val ground = t.accentDeep.copy(alpha = t.xray)
    val mark = lerp(t.accentFull, Color.White, t.xray)

    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(ground)
            .border(1.dp, t.hairline, shape)
            .pointerInput("dialer") { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        DialMark(mark, size * 0.52f)
    }
}

@Composable
private fun DialMark(color: Color, size: Dp) {
    Image(
        painter = painterResource(R.drawable.ic_phone),
        contentDescription = null,
        colorFilter = ColorFilter.tint(color),
        modifier = Modifier.size(size),
    )
}
