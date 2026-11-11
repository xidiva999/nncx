package dev.nncx.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The builder picks a hex. NNCx keeps the HUE and forces lightness/chroma into
 * a band that stays legible on near-black, so no choice can produce an
 * invisible launcher. Work is done in OkLCh because it is perceptually even —
 * clamping L in HSL would wreck some hues and not others.
 */
object Accent {

    private const val TARGET_L = 0.78f   // luminous, not deep
    private const val TARGET_C = 0.14f   // present, not neon

    /** Darkest a ground may be before white sitting on it starts to struggle. */
    private const val GROUND_L = 0.42f

    /**
     * The builder's colour, kept deep.
     *
     * On a paper-white page a luminous accent looks washed out, and the whole
     * point of picking a hex is to see that hex. So this keeps the hue AND the
     * darkness, clamping only if the choice is too pale to carry white text.
     */
    fun ground(hex: String): Color {
        val raw = parseHex(hex) ?: return Color(0xFF502B5D)
        val (l, a, b) = srgbToOklab(raw)
        if (l <= GROUND_L) {
            // Already dark enough — hand back exactly what was asked for.
            return Color(raw.first, raw.second, raw.third)
        }
        val chroma = hypot(a.toDouble(), b.toDouble()).toFloat()
        val hue = atan2(b.toDouble(), a.toDouble()).toFloat()
        var c = chroma
        repeat(24) {
            val candidate = oklchToSrgb(GROUND_L, c, hue)
            if (candidate != null) return candidate
            c -= 0.006f
        }
        return Color(0xFF502B5D)
    }

    /** The same hue lifted until it reads on near-black. */
    fun normalize(hex: String): Color {
        val raw = parseHex(hex) ?: return Color(0xFFB892FF)
        val (l, a, b) = srgbToOklab(raw)
        val chroma = hypot(a.toDouble(), b.toDouble()).toFloat()
        val hue = atan2(b.toDouble(), a.toDouble()).toFloat()

        // A grey input has no hue to preserve; fall back to NNCx violet.
        if (chroma < 0.015f) return Color(0xFFB892FF)

        var c = TARGET_C
        repeat(24) {
            val candidate = oklchToSrgb(TARGET_L, c, hue)
            if (candidate != null) return candidate
            c -= 0.006f                      // walk inward until in gamut
        }
        return Color(0xFFB892FF)
    }

    // ---- plumbing ---------------------------------------------------------

    private fun parseHex(hex: String): Triple<Float, Float, Float>? {
        val s = hex.trim().removePrefix("#")
        if (s.length != 6) return null
        val v = s.toLongOrNull(16) ?: return null
        return Triple(
            ((v shr 16) and 0xFF) / 255f,
            ((v shr 8) and 0xFF) / 255f,
            (v and 0xFF) / 255f,
        )
    }

    private fun toLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    private fun toSrgb(c: Float): Float =
        if (c <= 0.0031308f) c * 12.92f else 1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f

    private fun srgbToOklab(rgb: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
        val r = toLinear(rgb.first)
        val g = toLinear(rgb.second)
        val b = toLinear(rgb.third)

        val l = 0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b
        val m = 0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b
        val s = 0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b

        val l_ = cbrt(l.toDouble()).toFloat()
        val m_ = cbrt(m.toDouble()).toFloat()
        val s_ = cbrt(s.toDouble()).toFloat()

        return Triple(
            0.2104542553f * l_ + 0.7936177850f * m_ - 0.0040720468f * s_,
            1.9779984951f * l_ - 2.4285922050f * m_ + 0.4505937099f * s_,
            0.0259040371f * l_ + 0.7827717662f * m_ - 0.8086757660f * s_,
        )
    }

    /** Returns null when the colour falls outside sRGB. */
    private fun oklchToSrgb(lightness: Float, chroma: Float, hue: Float): Color? {
        val a = chroma * cos(hue.toDouble()).toFloat()
        val b = chroma * sin(hue.toDouble()).toFloat()

        val l_ = lightness + 0.3963377774f * a + 0.2158037573f * b
        val m_ = lightness - 0.1055613458f * a - 0.0638541728f * b
        val s_ = lightness - 0.0894841775f * a - 1.2914855480f * b

        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_

        val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
        val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
        val bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s

        val eps = 0.001f
        if (r < -eps || g < -eps || bl < -eps || r > 1f + eps || g > 1f + eps || bl > 1f + eps) return null

        return Color(
            toSrgb(r).coerceIn(0f, 1f),
            toSrgb(g).coerceIn(0f, 1f),
            toSrgb(bl).coerceIn(0f, 1f),
        )
    }
}
