package org.openrndr.color

import kotlinx.serialization.Serializable
import org.openrndr.math.Vector4
import org.openrndr.math.asDegrees
import org.openrndr.math.asRadians
import org.openrndr.math.mixAngle
import kotlin.math.*

/**
 * The [CIELChAB color space](https://en.wikipedia.org/wiki/CIELAB_color_space#Cylindrical_model)
 * is the cylindrical representation of the CIELAB color space.
 *
 * @param l luminance, in a range of 0.0 (darkest) to 100.0 (brightest)
 * @param c chroma
 * @param h hue in degrees, where a full rotation is 360.0 degrees
 * @param alpha alpha as a percentage between 0.0 and 1.0
 * @param ref reference white against which the color values are calculated
 *
 * @see ColorLABa
 */
@Serializable
data class ColorLCHABa(
    val l: Double,
    val c: Double,
    val h: Double,
    override val alpha: Double = 1.0,
    override val ref: ColorXYZa = ColorXYZa.NEUTRAL
) :
    ColorModel<ColorLCHABa>,
    ReferenceWhitePoint,
    ShadableColor<ColorLCHABa>,
    ChromaColor<ColorLCHABa>,
    HueShiftableColor<ColorLCHABa>,
    LuminosityColor<ColorLCHABa>,
    AlgebraicColor<ColorLCHABa> {
    companion object {
        fun findMaxChroma(l: Double, h: Double, ref: ColorXYZa): Double {
            var left = 0.0
            var right = 2000.0
            var bestGuess = left
            while (true) {

                if (right - left < 0.0001) {
                    return bestGuess
                }

                val leftTry = ColorLCHABa(l, left, h, 1.0, ref)
                val rightTry = ColorLCHABa(l, right, h, 1.0, ref)
                val middle = (left + right) / 2
                val middleTry = ColorLCHABa(l, middle, h, 1.0, ref)

                val leftValid = leftTry.toRGBa().let { it.minValue >= 0 && it.maxValue <= 1.0 }
                val rightValid = rightTry.toRGBa().let { it.minValue >= 0 && it.maxValue <= 1.0 }
                val middleValid = middleTry.toRGBa().let { it.minValue >= 0 && it.maxValue <= 1.0 }

                if (leftValid && middleValid && !rightValid) {
                    val newLeft = middle
                    val newRight = right
                    bestGuess = middle
                    left = newLeft
                    right = newRight
                }

                if (leftValid && !middleValid && !rightValid) {
                    val newLeft = left
                    val newRight = middle
                    left = newLeft
                    right = newRight
                }

                if (leftValid == middleValid && middleValid == rightValid) {
                    return bestGuess
                }
            }
        }


        /**
         * Converts a color from the LABa color space to the LCHABa color space.
         *
         * The conversion maps the lightness (`l`) from LABa to LCHABa, calculates the
         * chroma (`c`) as the magnitude of the `a` and `b` components, and computes the
         * hue (`h`) as the angle of the vector formed by the `a` and `b` components.
         * The alpha and reference white values are retained from the original LABa color.
         *
         * @param laba The color in the LABa color space to convert.
         * @return A `ColorLCHABa` instance representing the color in the LCHABa color space.
         */
        fun fromLABa(laba: ColorLABa): ColorLCHABa {
            val l = laba.l
            val c = sqrt(laba.a * laba.a + laba.b * laba.b)
            var h = atan2(laba.b, laba.a)

            if (h < 0) {
                h += PI * 2
            }

            h = h.asDegrees

            return ColorLCHABa(l, c, h, laba.alpha, laba.ref)
        }
    }


    /**
     * Converts the current color from the LCHABa color space to the LABa color space.
     *
     * The method computes the `a` and `b` coordinates using the chroma (`c`) and hue (`h`) values,
     * while retaining the lightness (`l`) and alpha values of the color.
     *
     * @return A `ColorLABa` instance representing the color in the LABa color space.
     */
    fun toLABa(): ColorLABa {
        val a = c * cos(h.asRadians)
        val b = c * sin(h.asRadians)
        return ColorLABa(l, a, b, alpha, ref)
    }

    /**
     * Converts the current color from the LCHABa color space to the XYZa color space.
     *
     * The method first transforms the color to the LABa color space using the `toLABa` method,
     * and then converts the resulting LABa color to the XYZa color space.
     *
     * @return A `ColorXYZa` instance representing the color in the XYZa color space.
     */
    fun toXYZa(): ColorXYZa = toLABa().toXYZa()

    override fun toRGBa(): ColorRGBa = toLABa().toXYZa().toRGBa()

    fun toLSHABa() = ColorLSHABa.fromLCHABa(this)

    override fun opacify(factor: Double) = copy(alpha = alpha * factor)
    override fun shade(factor: Double) = copy(l = l * factor)

    override fun plus(right: ColorLCHABa) =
        copy(l = l + right.l, c = c + right.c, h = h + right.h, alpha = alpha + right.alpha)

    override fun minus(right: ColorLCHABa) =
        copy(l = l - right.l, c = c - right.c, h = h - right.h, alpha = alpha - right.alpha)

    override fun times(scale: Double) = copy(l = l * scale, c = c * scale, h = h * scale, alpha = alpha * scale)
    override fun mix(other: ColorLCHABa, factor: Double) = mix(this, other, factor)

    override fun toVector4(): Vector4 = Vector4(l, c, h, alpha)
    override fun withChroma(chroma: Double): ColorLCHABa = copy(c = chroma)
    override val chroma
        get() = c
    override fun withHue(hue: Double): ColorLCHABa = copy(h = hue)

    override val hue: Double
        get() = h
    override fun withLuminosity(luminosity: Double): ColorLCHABa = copy(l = luminosity)
    override val luminosity: Double
        get() = l
}
/**
 * Weighted mix between two colors in the LChAB color space.
 * @param left the left-hand ColorLCHABa to mix
 * @param right the right-hand ColorLCHABa to mix
 * @param x the weighting of colors, a value 0.0 is equivalent to [left],
 * 1.0 is equivalent to [right] and at 0.5 both colors contribute to the result equally
 * @return a mix of [left] and [right] weighted by [x]
 */
fun mix(left: ColorLCHABa, right: ColorLCHABa, x: Double): ColorLCHABa {
    val sx = x.coerceIn(0.0, 1.0)
    return ColorLCHABa(
        (1.0 - sx) * left.l + sx * right.l,
        (1.0 - sx) * left.c + sx * right.c,
        mixAngle(left.h, right.h, sx),
        (1.0 - sx) * left.alpha + sx * right.alpha
    )
}
