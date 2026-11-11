package dev.nncx.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nncx.apps.AppEntry
import dev.nncx.apps.Icons
import dev.nncx.theme.LocalNncx

/**
 * A real app icon that loses its colour as you pull x-ray up.
 *
 * The saturation is a function of the same float that drives everything else,
 * so the drain is continuous: hold the gesture at 40% and the whole screen —
 * icons included — sits 40% of the way to the instrument.
 */
@Composable
fun AppIconView(
    entry: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalNncx.current
    val context = LocalContext.current

    // NOT produceState. Its state is built with a bare `remember { ... }`, with
    // no keys — so when a slot's app changes, the old icon survives and the
    // producer's null-check then declines to replace it. A dock slot could
    // never change its icon once set, which is why the dock looked frozen
    // whenever its contents changed: new pins, or the x-ray swap to My Builds.
    //
    // Keying the remember on the package is the whole fix.
    var icon by remember(entry.packageName) {
        mutableStateOf(Icons.cached(entry.packageName))
    }
    LaunchedEffect(entry.packageName) {
        if (icon == null) icon = Icons.load(context, entry)
    }

    val drain = remember(t.xray) {
        if (t.xray <= 0.01f) null
        else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1f - t.xray) })
    }

    val shape = RoundedCornerShape(t.corner)
    val current = icon

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            current == null -> Monogram(entry, shape)

            // Adaptive icons bleed past the mask by design: fill, then clip.
            current.adaptive -> Image(
                bitmap = current.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = drain,
                modifier = Modifier.fillMaxSize().clip(shape),
            )

            // Legacy icons already carry their own silhouette. Masking one
            // cuts its corners off, so it sits inset and unclipped instead.
            else -> Image(
                bitmap = current.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = drain,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
            )
        }
    }
}

/** Fallback only — what every slot used to be before icons existed. */
@Composable
private fun Monogram(entry: AppEntry, shape: RoundedCornerShape) {
    val t = LocalNncx.current
    Box(
        Modifier.fillMaxSize().clip(shape).background(t.surface),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            text = entry.label.trim().firstOrNull()?.uppercase() ?: "?",
            style = TextStyle(
                color = t.onSurface,
                fontSize = 16.sp,
                fontFamily = if (t.isXray) t.dataFamily else t.uiFamily,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
