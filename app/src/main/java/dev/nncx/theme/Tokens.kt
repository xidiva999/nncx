package dev.nncx.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.platform.LocalContext
import dev.nncx.settings.Settings

/**
 * There is exactly one mode in NNCx and it is a number.
 *
 *   xray = 0f  -> Pixel pole:   warm, soft, light, wallpaper visible, no accent
 *   xray = 1f  -> Nothing pole: cold, sharp, near-black, hairline grid, accent live
 *
 * Nothing in the tree knows there are "two themes". Every token below is a
 * function of that float, which is why the transform can be dragged and held
 * at 40%.
 */
@Immutable
data class NncxTokens(
    val xray: Float,
    // surfaces
    val scrim: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceDim: Color,
    val hairline: Color,
    // semantics
    val accent: Color,
    val accentFull: Color,
    val accentDeep: Color,
    val danger: Color,
    // geometry
    val corner: Dp,
    val gutter: Dp,
    val rowHeight: Dp,
    val gridStep: Dp,
    val gridAlpha: Float,
    // type
    val dataFamily: FontFamily,
    val uiFamily: FontFamily,
) {
    val isXray: Boolean get() = xray > 0.5f
}

// ---- the two poles --------------------------------------------------------
//
// Both are solid. No wallpaper behind, no translucency, no gradients: every
// surface is one flat colour, and structure comes from hairlines instead.

private val NormalBg = Color(0xFFF7F7F5)
private val NormalSurface = Color(0xFFECECE9)
private val NormalOn = Color(0xFF15151A)
private val NormalOnDim = Color(0xFF6B6B74)
private val NormalHairline = Color(0xFFDEDEDA)

private val XrayBg = Color(0xFF0A0A0C)
private val XraySurface = Color(0xFF17171B)
private val XrayOn = Color(0xFFF2F2F5)
private val XrayOnDim = Color(0xFF75757F)
private val XrayHairline = Color(0xFF2B2B32)

private val Danger = Color(0xFFFF4D4D)   // never configurable

fun nncxTokens(xray: Float, accentDeep: Color, accentLight: Color): NncxTokens {
    val x = xray.coerceIn(0f, 1f)
    return NncxTokens(
        xray = x,
        scrim = lerp(NormalBg, XrayBg, x),
        surface = lerp(NormalSurface, XraySurface, x),
        onSurface = lerp(NormalOn, XrayOn, x),
        onSurfaceDim = lerp(NormalOnDim, XrayOnDim, x),
        hairline = lerp(NormalHairline, XrayHairline, x),
        // The accent does not exist in normal mode. It floods in with x.
        // The builder's hex, everywhere, unlifted. On near-black that is a
        // 1.74:1 ratio against #0A0A0C — dim by design, not by accident.
        accent = accentDeep.copy(alpha = x),
        accentFull = accentDeep,
        accentDeep = accentDeep,
        danger = Danger.copy(alpha = x),
        corner = lerp(28.dp, 3.dp, x),
        gutter = lerp(24.dp, 12.dp, x),
        rowHeight = lerp(60.dp, 44.dp, x),
        gridStep = 24.dp,
        gridAlpha = 0.07f * x,
        dataFamily = FontFamily.Monospace,
        uiFamily = FontFamily.SansSerif,
    )
}

val LocalNncx: ProvidableCompositionLocal<NncxTokens> =
    compositionLocalOf { nncxTokens(0f, Color(0xFF502B5D), Color(0xFFB892FF)) }

@Composable
fun NncxTheme(xray: Float, content: @Composable () -> Unit) {
    val hex = Settings.get(LocalContext.current).accent
    val deep = remember(hex) { Accent.ground(hex) }
    val light = remember(hex) { Accent.normalize(hex) }
    val tokens = nncxTokens(xray, deep, light)
    androidx.compose.runtime.CompositionLocalProvider(LocalNncx provides tokens, content = content)
}
