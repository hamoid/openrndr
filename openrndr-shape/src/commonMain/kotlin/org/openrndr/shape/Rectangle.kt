@file:Suppress("unused")

package org.openrndr.shape

import kotlinx.serialization.Serializable
import org.openrndr.math.LinearType
import org.openrndr.math.Vector2
import org.openrndr.math.YPolarity
import org.openrndr.math.clamp
import kotlin.jvm.JvmName
import kotlin.jvm.JvmRecord
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * Creates a new axis-aligned [Rectangle].
 *
 * [Rectangle] is only a data structure with no visible representation,
 * although it can be drawn by calling [org.openrndr.draw.Drawer.rectangle].
 *
 * Also see [IntRectangle].
 */
@Serializable
@JvmRecord
data class Rectangle(val corner: Vector2, val width: Double, val height: Double = width) : Movable, Scalable2D,
    ShapeProvider, ShapeContourProvider, LinearType<Rectangle> {

    val xRange
        get() = min(corner.x, corner.x + width)..<max(corner.x, corner.x + width)

    val yRange
        get() = min(corner.y, corner.y + height)..<max(corner.y, corner.y + height)


    /** The center of the [Rectangle]. */
    val center: Vector2
        get() = corner + Vector2(width / 2, height / 2)

    /** The unitless area covered by this [Rectangle]. */
    val area: Double
        get() = width * height

    /** The dimensions of the [Rectangle]. */
    val dimensions: Vector2
        get() = Vector2(width, height)

    override val scale: Vector2
        get() = dimensions

    override fun position(u: Double, v: Double): Vector2 {
        return corner + Vector2(u * width, v * height)
    }

    /** The [x]-coordinate of the top-left corner. */
    val x: Double get() = corner.x

    /** The [y]-coordinate of the top-left corner. */
    val y: Double get() = corner.y

    /** Returns [Shape] representation of the [Rectangle]. */
    override val shape: Shape get() = Shape(listOf(contour))

    /** Returns [ShapeContour] representation of the [Rectangle]. */
    override val contour: ShapeContour
        get() {
            return if (corner == Vector2.INFINITY || corner.x != corner.x || corner.y != corner.y || width != width || height != height) {
                ShapeContour.EMPTY
            } else {
                ShapeContour.fromPoints(
                    listOf(
                        corner, corner + Vector2(width, 0.0),
                        corner + Vector2(width, height),
                        corner + Vector2(0.0, height)
                    ), true, YPolarity.CW_NEGATIVE_Y
                )
            }
        }

    /**
     * Creates a new [Rectangle] with sides offset both horizontally and vertically by specified amount.
     *
     * The [Rectangle] sides are shifted outwards if [offset] values are > 0 or inwards if the values are < 0.
     */
    fun offsetEdges(offset: Double, offsetY: Double = offset): Rectangle {
        return Rectangle(Vector2(corner.x - offset, corner.y - offsetY), width + 2 * offset, height + 2 * offsetY)
    }

    /**
     * Creates a new [Rectangle] with dimensions scaled by [scale] and [scaleY].
     *
     * @param scale the x scale factor
     * @param scaleY the y scale factor, default is [scale]
     * @param anchorU x coordinate of the scaling anchor in u parameter space, default is 0.5 (center)
     * @param anchorV y coordinate of the scaling anchor in v parameter space, default is 0.5 (center)
     */
    @Deprecated("Vague naming", ReplaceWith("scaledBy(scale, scaleY)"))
    fun scale(scale: Double, scaleY: Double = scale, anchorU: Double = 0.5, anchorV: Double = 0.5): Rectangle {
        return scaledBy(scale, scaleY, anchorU, anchorV)
    }

    @Deprecated("Doesn't account for anchor placement", ReplaceWith("scaledBy(scale, scaleY)"))
    fun scaled(scale: Double, scaleY: Double = scale): Rectangle {
        return Rectangle(corner, width * scale, height * scaleY)
    }

    /** Creates a new [Rectangle] with width set to [fitWidth] and height scaled proportionally. */
    fun widthScaledTo(fitWidth: Double): Rectangle {
        val scale = fitWidth / width
        return Rectangle(corner, fitWidth, height * scale)
    }

    /** Creates a new [Rectangle] with height set to [fitHeight] and width scaled proportionally. */
    fun heightScaledTo(fitHeight: Double): Rectangle {
        val scale = fitHeight / height
        return Rectangle(corner, width * scale, fitHeight)
    }

    /** Creates a new [Rectangle] with the same size but the current position offset by [offset] amount. */
    @Deprecated("Vague naming", ReplaceWith("movedBy(offset)"))
    fun moved(offset: Vector2): Rectangle {
        return Rectangle(corner + offset, width, height)
    }

    override fun movedBy(offset: Vector2): Rectangle = Rectangle(corner + offset, width, height)

    override fun movedTo(position: Vector2): Rectangle = Rectangle(position, width, height)

    override fun scaledBy(xScale: Double, yScale: Double, uAnchor: Double, vAnchor: Double): Rectangle {
        val anchorPosition = position(uAnchor, vAnchor)
        val d = corner - anchorPosition
        val nd = anchorPosition + d * Vector2(xScale, yScale)
        return Rectangle(nd, width * xScale, height * yScale)
    }

    override fun scaledBy(scale: Double, uAnchor: Double, vAnchor: Double): Rectangle =
        scaledBy(scale, scale, uAnchor, vAnchor)

    override fun scaledTo(width: Double, height: Double): Rectangle = Rectangle(corner, width, height)

    override fun scaledTo(size: Double): Rectangle = scaledTo(size, size)

    /**
     * Creates a Rectangle mirrored around a vertical axis. [u] specifies the axis position.
     * Defaults to 0.5 (center). Left edge = 0.0, right edge = 1.0.
     * Frequently used for mirroring images or video.
     */
    fun flippedHorizontally(u: Double = 0.5): Rectangle = if (u == 0.5) {
        scaledBy(xScale = -1.0, 1.0)
    } else {
        scaledBy(xScale = -1.0, 1.0).movedBy(Vector2((u * 2 - 1) * width, 0.0))
    }

    /**
     * Creates a Rectangle mirrored around a horizontal axis. [v] specifies the axis position.
     * Defaults to 0.5 (center). Top edge = 0.0, bottom edge = 1.0.
     * Frequently used for mirroring images or video.
     */
    fun flippedVertically(v: Double = 0.5): Rectangle = if (v == 0.5) {
        scaledBy(xScale = 1.0, -1.0)
    } else {
        scaledBy(xScale = 1.0, -1.0).movedBy(Vector2(0.0, (v * 2 - 1) * height))
    }

    /**
     * Returns a horizontal [LineSegment] specified by [v].
     * Top edge [v] = 0.0, bottom edge [v] = 1.0.
     */
    fun horizontal(v: Double = 0.5): LineSegment = LineSegment(
        position(0.0, v), position(1.0, v)
    )

    /**
     * Returns a vertical [LineSegment] specified by [u].
     * Left edge [u] = 0.0, right edge [u] = 1.0.
     */
    fun vertical(u: Double = 0.5): LineSegment = LineSegment(
        position(u, 0.0), position(u, 1.0)
    )

    /**
     * Returns true if given [point] is inside the [Rectangle].
     */
    operator fun contains(point: Vector2): Boolean {
        return (point.x >= corner.x &&
                point.x < corner.x + width &&
                point.y >= corner.y &&
                point.y < corner.y + height)
    }

    /**
     * Tests if the **areas** of two rectangles intersect.
     */
    fun intersects(other: Rectangle): Boolean {
        val above = y + height < other.y
        val below = y > other.y + other.height
        val rightOf = x > other.x + other.width
        val leftOf = x + width < other.x
        return !(above || below || leftOf || rightOf)
    }

    companion object {
        /** Creates a new [Rectangle] by specifying the [center] position with dimensions [width] and [height]. */
        fun fromCenter(center: Vector2, width: Double, height: Double = width) =
            fromAnchor(Vector2(0.5, 0.5), center, width, height)


        /** Creates a new [Rectangle] by specifying the [anchorUV], [anchor] positions with dimensions [width] and [height]. */
        fun fromAnchor(anchorUV: Vector2, anchor: Vector2, width: Double, height: Double = width) =
            Rectangle(anchor.x - width * anchorUV.x, anchor.y - height * anchorUV.y, width, height)

        /** A zero-length [Rectangle]. */
        val EMPTY = Rectangle(0.0, 0.0, 0.0, 0.0)
    }

    override operator fun times(scale: Double) = Rectangle(corner * scale, width * scale, height * scale)

    override operator fun div(scale: Double) = Rectangle(corner / scale, width / scale, height / scale)

    override operator fun plus(right: Rectangle) =
        Rectangle(corner + right.corner, width + right.width, height + right.height)

    override operator fun minus(right: Rectangle) =
        Rectangle(corner - right.corner, width - right.width, height - right.height)


    /**
     * Return a sub-rectangle spanning over the provided u and v ranges.
     *
     * For example to obtain one quadrant of the rectangle one uses `.sub(0.0 .. 0.5, 0.0 .. 0.5)`
     */
    fun sub(u: ClosedFloatingPointRange<Double>, v: ClosedFloatingPointRange<Double>): Rectangle {
        return sub(u.start, v.start, u.endInclusive, v.endInclusive)
    }

    /**
     * Return a sub-rectangle spanning from (u0, v0) to (u1, v1)
     *
     * For example to obtain one quadrant of the rectangle one uses `.sub(0.0, 0.0, 0.5, 0.5)`
     */
    fun sub(u0: Double, v0: Double, u1: Double, v1: Double): Rectangle {
        val p0 = position(u0, v0)
        val p1 = position(u1, v1)
        val width = p1.x - p0.x
        val height = p1.y - p0.y
        return Rectangle(p0.x, p0.y, width, height)
    }

    /**
     * Casts to [IntRectangle].
     */
    fun toInt() = IntRectangle(x.toInt(), y.toInt(), width.toInt(), height.toInt())

    val majorAxis: Vector2.Axis
        get() {
            return if (width >= height) {
                Vector2.Axis.X
            } else {
                Vector2.Axis.Y
            }
        }

    val minorAxis: Vector2.Axis
        get() {
            return if (width <= height) {
                Vector2.Axis.X
            } else {
                Vector2.Axis.Y
            }
        }

    fun ratio(axis: Vector2.Axis = majorAxis): Rectangle {
        val scale = 1.0 / dimensions.dot(axis.direction)
        return fromCenter(Vector2.ZERO, width * scale, height * scale)
    }

    val normalized: Rectangle
        get() {
            var nx = x
            var ny = y
            if (width < 0) {
                nx += width
            }
            if (height < 0) {
                ny += height
            }
            return Rectangle(nx, ny, width.absoluteValue, height.absoluteValue)
        }
}

/**
 * Calculates [Rectangle]-bounds from a [List] of [Vector2] instances.
 *
 * The provided list should consist of more than one item for optimal results.
 */
val List<Vector2>.bounds: Rectangle
    @JvmName("getVector2Bounds") get() {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        this.forEach {
            minX = min(minX, it.x)
            maxX = max(maxX, it.x)
            minY = min(minY, it.y)
            maxY = max(maxY, it.y)
        }
        return Rectangle(Vector2(minX, minY), maxX - minX, maxY - minY)
    }

/** Calculates [Rectangle]-bounds for a list of [Rectangle] instances. */
val List<Rectangle>.bounds: Rectangle
    @JvmName("getRectangleBounds") get() {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        this.forEach {
            if (it != Rectangle.EMPTY) {
                minX = min(minX, it.x)
                maxX = max(maxX, it.x + it.width)
                minY = min(minY, it.y)
                maxY = max(maxY, it.y + it.height)
            }
        }
        return Rectangle(Vector2(minX, minY), maxX - minX, maxY - minY)
    }

/** Determines whether rectangles [a] and [b] intersect. */
@Deprecated(
    "use Rectangle.intersects(Rectangle) instead",
    ReplaceWith("a.intersects(b)")
)
fun intersects(a: Rectangle, b: Rectangle) = a.intersects(b)

/**
 * Clamps a [Vector2] within the bounds of the [bounds] `Rectangle`.
 */
fun Vector2.clamp(bounds: Rectangle) =
    this.clamp(bounds.corner, bounds.corner + bounds.dimensions)

/**
 * Remaps [Vector2] from a position on the [sourceRectangle] to
 * a proportionally equivalent position on the [targetRectangle].
 *
 * @param clamp Clamps remapped value within the bounds of [targetRectangle].
 */
fun Vector2.map(sourceRectangle: Rectangle, targetRectangle: Rectangle, clamp: Boolean = false): Vector2 {
    val remapped =
        (this - sourceRectangle.corner) / sourceRectangle.dimensions * targetRectangle.dimensions + targetRectangle.corner
    return if (clamp) remapped.clamp(targetRectangle) else remapped
}

/**
 * Maps all elements in a `List<Vector2>`
 * from [sourceRectangle] to [targetRectangle].
 * If [clamp] is true all elements are clamped within the bounds of [targetRectangle].
 */
fun List<Vector2>.map(sourceRectangle: Rectangle, targetRectangle: Rectangle, clamp: Boolean = false): List<Vector2> =
    this.map { it.map(sourceRectangle, targetRectangle, clamp) }


fun Rectangle(x: Double, y: Double, width: Double, height: Double = width) = Rectangle(Vector2(x, y), width, height)


