package dev.nncx.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

/**
 * NNCx has two independent axes:
 *
 *   fold = how much room I have
 *   x    = how much truth I want
 *
 * This is the first one. Postures are not screen sizes stretched — they are
 * different layouts, because a 1:1 canvas wants something a 23:9 one does not.
 */
enum class Posture {
    /** Closed. One hand, two seconds, thumb low. */
    COVER,

    /** Open and flat. Two hands, real work, a square canvas that wants panes. */
    FLAT,

    /** Half open, hinge horizontal. On a desk: glance above, input below. */
    TABLETOP,
}

data class PostureInfo(
    val posture: Posture,
    val widthDp: Int,
    val heightDp: Int,
    /** Where the crease falls, in dp from the start edge. Null when there is none. */
    val hingeStartDp: Dp? = null,
    val hingeSizeDp: Dp = 0.dp,
) {
    val isWide: Boolean get() = posture == Posture.FLAT
}

/** Minimum width at which panes beat a single column. */
private const val WIDE_DP = 600

@Composable
fun rememberPosture(): PostureInfo {
    val context = LocalContext.current
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }

    var fold by remember { mutableStateOf<FoldingFeature?>(null) }

    LaunchedEffect(activity) {
        val a = activity ?: return@LaunchedEffect
        WindowInfoTracker.getOrCreate(a)
            .windowLayoutInfo(a)
            .collect { info ->
                fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            }
    }

    val w = config.screenWidthDp
    val h = config.screenHeightDp
    val f = fold

    val posture = when {
        f != null &&
            f.state == FoldingFeature.State.HALF_OPENED &&
            f.orientation == FoldingFeature.Orientation.HORIZONTAL -> Posture.TABLETOP

        w >= WIDE_DP -> Posture.FLAT
        else -> Posture.COVER
    }

    // The hinge is a layout primitive, not an obstacle: let it be the divider
    // between panes rather than something content has to dodge.
    val hingeStart = f?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.let { with(density) { it.bounds.left.toDp() } }
    val hingeSize = f?.takeIf { it.orientation == FoldingFeature.Orientation.VERTICAL }
        ?.let { with(density) { (it.bounds.right - it.bounds.left).toDp() } } ?: 0.dp

    return PostureInfo(
        posture = posture,
        widthDp = w,
        heightDp = h,
        hingeStartDp = hingeStart,
        hingeSizeDp = hingeSize,
    )
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
