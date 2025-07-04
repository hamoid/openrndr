package org.openrndr.shape

import kotlinx.serialization.Serializable
import org.openrndr.math.*
import kotlin.math.pow

private fun sumDifferences(points: List<Vector3>) =
    (0 until points.size - 1).sumOf { (points[it] - points[it + 1]).length }


class SegmentProjection3D(val segment: Segment3D, val projection: Double, val distance: Double, val point: Vector3)

@Serializable
class Segment3D(
    override val start: Vector3,
    override val control: List<Vector3>,
    override val end: Vector3
) : BezierSegment<Vector3>, LinearType<Segment3D> {

    private var lut: List<Vector3>? = null


    fun lut(size: Int = 100): List<Vector3> {
        if (lut == null || lut!!.size != size) {
            lut = (0..size).map { position((it.toDouble() / size)) }
        }
        return lut!!
    }

    fun on(point: Vector3, error: Double = 5.0): Double? {
        val lut = lut()
        var hits = 0
        var t = 0.0
        for (i in lut.indices) {
            if ((lut[i] - point).squaredLength < error * error) {
                hits++
                t += i.toDouble() / lut.size
            }
        }
        return if (hits > 0) t / hits else null
    }

    private fun closest(points: List<Vector3>, query: Vector3): Pair<Int, Vector3> {
        var closestIndex = 0
        var closestValue = points[0]

        var closestDistance = Double.POSITIVE_INFINITY
        for (i in points.indices) {
            val distance = (points[i] - query).squaredLength
            if (distance < closestDistance) {
                closestIndex = i
                closestValue = points[i]
                closestDistance = distance
            }
        }
        return Pair(closestIndex, closestValue)
    }

    /**
     * Projects a given point onto the segment, calculating the closest position on the segment
     * and its associated properties such as projection parameter, distance, and position.
     *
     * @param point The 3D point to project onto the segment.
     * @return A SegmentProjection3D object containing the projection parameter, the squared distance
     *         between the point and the projected position, and the projected position itself.
     */
    fun project(point: Vector3): SegmentProjection3D {
        // based on bezier.js
        val lut = lut()
        val l = (lut.size - 1).toDouble()
        val closest = closest(lut, point)

        var closestDistance = (point - closest.second).squaredLength

        if (closest.first == 0 || closest.first == lut.size - 1) {
            val t = closest.first.toDouble() / l
            return SegmentProjection3D(this, t, closestDistance, closest.second)
        } else {
            val t1 = (closest.first - 1) / l
            val t2 = (closest.first + 1) / l
            val step = 0.1 / l

            var t = t1
            var ft = t1

            while (t < t2 + step) {
                val p = position(t)
                val d = (p - point).squaredLength
                if (d < closestDistance) {
                    closestDistance = d
                    ft = t
                }
                t += step
            }
            val p = position(ft)
            return SegmentProjection3D(this, ft, closestDistance, p)
        }
    }

    /**
     * Applies a transformation matrix to the segment, transforming its start, end,
     * and control points, and returns a new transformed segment.
     *
     * @param transform The 4x4 transformation matrix to apply to the segment.
     * @return A new instance of Segment3D representing the transformed segment.
     */
    fun transform(transform: Matrix44): Segment3D {
        val tstart = (transform * (start.xyz1)).div
        val tend = (transform * (end.xyz1)).div
        val tcontrol = when (control.size) {
            2 -> listOf((transform * control[0].xyz1).div, (transform * control[1].xyz1).div)
            1 -> listOf((transform * control[0].xyz1).div)
            else -> emptyList()
        }
        return Segment3D(tstart, tcontrol, tend)
    }

    override val length: Double by lazy {
        when (control.size) {
            0 -> (end - start).length
            1, 2 -> sumDifferences(adaptivePositions())
            else -> throw RuntimeException("unsupported number of control points")
        }
    }

    override fun position(ut: Double): Vector3 {
        val t = ut.coerceIn(0.0, 1.0)
        return when (control.size) {
            0 -> Vector3(
                start.x * (1.0 - t) + end.x * t,
                start.y * (1.0 - t) + end.y * t,
                start.z * (1.0 - t) + end.z * t
            )

            1 -> bezier(start, control[0], end, t)
            2 -> bezier(start, control[0], control[1], end, t)
            else -> throw RuntimeException("unsupported number of control points")
        }
    }

    /**
     * Computes the parameter values at which the extrema (minimums and maximums) occur
     * for the given segment. This includes analyzing changes in direction for the x
     * and y components of control points or derivatives, depending on the control size.
     *
     * @return A list of parameter values representing the extrema, filtered to be
     * within the range [0.0, 1.0].
     */
    fun extrema(): List<Double> {
        val dpoints = dPoints()
        return when {
            linear -> emptyList()
            control.size == 1 -> {
                val xRoots = roots(dpoints[0].map { it.x })
                val yRoots = roots(dpoints[0].map { it.y })
                (xRoots + yRoots).distinct().sorted().filter { it in 0.0..1.0 }
            }

            control.size == 2 -> {
                val xRoots = roots(dpoints[0].map { it.x }) + roots(dpoints[1].map { it.x })
                val yRoots = roots(dpoints[0].map { it.y }) + roots(dpoints[1].map { it.y })
                (xRoots + yRoots).distinct().sorted().filter { it in 0.0..1.0 }
            }

            else -> throw RuntimeException("not supported")
        }
    }

    /**
     * Computes the points in 3D space where the extrema (minimums and maximums) occur
     * for the given 3D segment. These extrema correspond to parameter values derived
     * from the segment's control points or derivatives and are transformed into their
     * respective positions in 3D space.
     *
     * @return A list of 3D vectors ([Vector3]) representing the extrema points of the segment.
     */
    fun extremaPoints(): List<Vector3> = extrema().map { position(it) }


    private fun dPoints(): List<List<Vector3>> {
        val points = listOf(start) + control + listOf(end)
        var d = points.size
        var c = d - 1
        val dpoints = mutableListOf<List<Vector3>>()
        var p = points
        while (d > 1) {
            val list = mutableListOf<Vector3>()
            for (j in 0 until c) {
                list.add(Vector3(c * (p[j + 1].x - p[j].x), c * (p[j + 1].y - p[j].y), c * (p[j + 1].z - p[j].z)))
            }
            dpoints.add(list)
            p = list
            d--
            c--
        }
        return dpoints
    }


    /**
     * Cubic version of segment
     */
    override val cubic: Segment3D
        get() = when {
            control.size == 2 -> this
            control.size == 1 -> {
                Segment3D(
                    start,
                    start * (1.0 / 3.0) + control[0] * (2.0 / 3.0),
                    control[0] * (2.0 / 3.0) + end * (1.0 / 3.0),
                    end
                )
            }

            linear -> {
                val delta = end - start
                Segment3D(
                    start,
                    start + delta * (1.0 / 3.0),
                    start + delta * (2.0 / 3.0),
                    end
                )
            }

            else -> throw RuntimeException("cannot convert to cubic segment")
        }

    override fun derivative(t: Double): Vector3 = when {
        linear -> start - end
        control.size == 1 -> derivative(start, control[0], end, t)
        control.size == 2 -> derivative(start, control[0], control[1], end, t)
        else -> throw RuntimeException("not implemented")
    }

    override fun derivative2(t: Double): Vector3 = when {
        linear -> Vector3.ZERO
        control.size == 1 -> cubic.derivative2(t)
        control.size == 2 -> derivative2(
            start,
            control[0],
            control[1],
            end,
            t
        )

        else -> throw RuntimeException("not implemented")
    }

    override fun curvature(t: Double): Double {
        val d = derivative(t)
        val dd = derivative2(t)
        val numerator = d.cross(dd).length
        val denominator = d.length.pow(3.0)
        return numerator / denominator
    }

    override val reverse: Segment3D
        get() {
            return when (control.size) {
                0 -> Segment3D(end, start)
                1 -> Segment3D(end, control[0], start)
                2 -> Segment3D(end, control[1], control[0], start)
                else -> throw RuntimeException("unsupported number of control points")
            }
        }

    /**
     * Split the contour
     * @param t the point to split the contour at
     * @return array of parts, depending on the split point this is one or two entries long
     */
    override fun split(t: Double): Array<Segment3D> {
        val u = t.coerceIn(0.0, 1.0)

        if (linear) {
            val cut = start + (end.minus(start) * u)
            return arrayOf(Segment3D(start, cut), Segment3D(cut, end))
        } else {
            when (control.size) {
                2 -> {
                    val z = u
                    val z2 = z * z
                    val z3 = z * z * z
                    val iz = 1 - z
                    val iz2 = iz * iz
                    val iz3 = iz * iz * iz

                    val lsm = Matrix44(
                        1.0, 0.0, 0.0, 0.0,
                        iz, z, 0.0, 0.0,
                        iz2, 2.0 * iz * z, z2, 0.0,
                        iz3, 3.0 * iz2 * z, 3.0 * iz * z2, z3
                    )

                    val px = Vector4(start.x, control[0].x, control[1].x, end.x)
                    val py = Vector4(start.y, control[0].y, control[1].y, end.y)
                    val pz = Vector4(start.z, control[0].z, control[1].z, end.z)

                    val plx = lsm * px//.multiply(lsm)
                    val ply = lsm * py// py.multiply(lsm)
                    val plz = lsm * pz// py.multiply(lsm)

                    val pl0 = Vector3(plx.x, ply.x, plz.x)
                    val pl1 = Vector3(plx.y, ply.y, plz.y)
                    val pl2 = Vector3(plx.z, ply.z, plz.z)
                    val pl3 = Vector3(plx.w, ply.w, plz.w)

                    val left = Segment3D(pl0, pl1, pl2, pl3)

                    val rsm = Matrix44(
                        iz3, 3.0 * iz2 * z, 3.0 * iz * z2, z3,
                        0.0, iz2, 2.0 * iz * z, z2,
                        0.0, 0.0, iz, z,
                        0.0, 0.0, 0.0, 1.0
                    )

                    val prx = rsm * px
                    val pry = rsm * py
                    val prz = rsm * pz

                    val pr0 = Vector3(prx.x, pry.x, prz.x)
                    val pr1 = Vector3(prx.y, pry.y, prz.y)
                    val pr2 = Vector3(prx.z, pry.z, prz.z)
                    val pr3 = Vector3(prx.w, pry.w, prz.w)

                    val right = Segment3D(pr0, pr1, pr2, pr3)

                    return arrayOf(left, right)
                }

                1 -> {
                    val z = u
                    val iz = 1 - z
                    val iz2 = iz * iz
                    val z2 = z * z

                    val lsm = Matrix44(
                        1.0, 0.0, 0.0, 0.0,
                        iz, z, 0.0, 0.0,
                        iz2, 2.0 * iz * z, z2, 0.0,
                        0.0, 0.0, 0.0, 0.0
                    )

                    val px = Vector4(start.x, control[0].x, end.x, 0.0)
                    val py = Vector4(start.y, control[0].y, end.y, 0.0)
                    val pz = Vector4(start.z, control[0].z, end.z, 0.0)

                    val plx = lsm * px
                    val ply = lsm * py
                    val plz = lsm * pz

                    val left = Segment3D(
                        Vector3(plx.x, ply.x, plz.x),
                        Vector3(plx.y, ply.y, plz.y),
                        Vector3(plx.z, ply.z, plz.z)
                    )

                    val rsm = Matrix44(
                        iz2, 2.0 * iz * z, z2, 0.0,
                        0.0, iz, z, 0.0,
                        0.0, 0.0, 1.0, 0.0,
                        0.0, 0.0, 0.0, 0.0
                    )

                    val prx = rsm * px
                    val pry = rsm * py
                    val prz = rsm * pz

                    val right = Segment3D(
                        Vector3(prx.x, pry.x, prz.x),
                        Vector3(prx.y, pry.y, prz.y),
                        Vector3(prx.z, pry.z, prz.z)
                    )

                    return arrayOf(left, right)

                }

                else -> throw RuntimeException("not implemented")
            }
        }
    }

    override fun toString(): String {
        return "Segment(start=$start, end=$end, control=${control})"
    }

    fun copy(start: Vector3 = this.start, control: List<Vector3> = this.control, end: Vector3 = this.end): Segment3D {
        return Segment3D(start, control, end)
    }

    val bounds: Box
        get() = (listOf(start, end) + extremaPoints()).bounds

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as Segment3D

        if (start != other.start) return false
        if (end != other.end) return false
        return control == other.control
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + end.hashCode()
        result = 31 * result + control.hashCode()
        return result
    }

    override fun tForLength(length: Double): Double {
        if (type == SegmentType.LINEAR) {
            return (length / this.length).coerceIn(0.0, 1.0)
        }

        val segmentLength = this.length
        val cLength = length.coerceIn(0.0, segmentLength)

        if (cLength == 0.0) {
            return 0.0
        }
        if (cLength >= segmentLength) {
            return 1.0
        }
        var summedLength = 0.0
        lut(100)
        val cLut = lut ?: error("no lut")
        val partitionCount = cLut.size - 1

        val dt = 1.0 / partitionCount
        for ((index, _ /*point*/) in lut!!.withIndex()) {
            if (index < lut!!.size - 1) {
                val p0 = cLut[index]
                val p1 = cLut[index + 1]
                val partitionLength = p0.distanceTo(p1)
                summedLength += partitionLength
                if (summedLength >= length) {
                    val localT = index.toDouble() / partitionCount
                    val overshoot = summedLength - length
                    return localT + (overshoot / partitionLength) * dt
                }
            }
        }
        return 1.0
    }

    override operator fun times(scale: Double): Segment3D {
        return when (type) {
            SegmentType.LINEAR -> Segment3D(start * scale, end * scale)
            SegmentType.QUADRATIC -> Segment3D(
                start * scale,
                control[0] * scale,
                end * scale
            )

            SegmentType.CUBIC -> Segment3D(
                start * scale,
                control[0] * scale,
                control[1] * scale,
                end * scale
            )
        }
    }


    override operator fun div(scale: Double): Segment3D {
        return when (type) {
            SegmentType.LINEAR -> Segment3D(start / scale, end / scale)
            SegmentType.QUADRATIC -> Segment3D(
                start / scale,
                control[0] / scale,
                end / scale
            )

            SegmentType.CUBIC -> Segment3D(
                start / scale,
                control[0] / scale,
                control[1] / scale,
                end / scale
            )
        }
    }

    override operator fun minus(right: Segment3D): Segment3D {
        return if (this.type == right.type) {
            when (type) {
                SegmentType.LINEAR -> Segment3D(
                    start - right.start,
                    end - right.end
                )

                SegmentType.QUADRATIC -> Segment3D(
                    start - right.start,
                    control[0] - right.control[0],
                    end - right.end
                )

                SegmentType.CUBIC -> Segment3D(
                    start - right.start,
                    control[0] - right.control[0],
                    control[1] - right.control[1],
                    end - right.end
                )
            }
        } else {
            if (this.type.ordinal > right.type.ordinal) {
                when (type) {
                    SegmentType.LINEAR -> error("impossible?")
                    SegmentType.QUADRATIC -> this - right.quadratic
                    SegmentType.CUBIC -> this - right.cubic
                }
            } else {
                when (right.type) {
                    SegmentType.LINEAR -> error("impossible?")
                    SegmentType.QUADRATIC -> this.quadratic - right
                    SegmentType.CUBIC -> this.cubic - right
                }
            }
        }
    }

    override operator fun plus(right: Segment3D): Segment3D {
        return if (this.type == right.type) {
            when (type) {
                SegmentType.LINEAR -> Segment3D(
                    start + right.start,
                    end + right.end
                )

                SegmentType.QUADRATIC -> Segment3D(
                    start + right.start,
                    control[0] + right.control[0],
                    end + right.end
                )

                SegmentType.CUBIC -> Segment3D(
                    start + right.start,
                    control[0] + right.control[0],
                    control[1] + right.control[1],
                    end + right.end
                )
            }
        } else {
            if (this.type.ordinal > right.type.ordinal) {
                when (type) {
                    SegmentType.LINEAR -> error("impossible?")
                    SegmentType.QUADRATIC -> this + right.quadratic
                    SegmentType.CUBIC -> this + right.cubic
                }
            } else {
                when (right.type) {
                    SegmentType.LINEAR -> error("impossible?")
                    SegmentType.QUADRATIC -> this.quadratic + right
                    SegmentType.CUBIC -> this.cubic + right
                }
            }
        }
    }

    /** Converts the [Segment2D] to a quadratic Bézier curve. */
    val quadratic: Segment3D
        get() = when {
            control.size == 1 -> this
            linear -> {
                val delta = end - start
                Segment3D(start, start + delta * (1.0 / 2.0), end)
            }

            else -> error("cannot convert to quadratic segment")
        }

}

/**
 * Creates a 3D segment defined by a start and an end point in 3D space.
 *
 * @param start The starting point of the segment represented as a [Vector3].
 * @param end The ending point of the segment represented as a [Vector3].
 */
fun Segment3D(start: Vector3, end: Vector3) = Segment3D(start, emptyList(), end)


/**
 * Quadratic bezier segment constructor
 * @param start starting point of the segment
 * @param c0 control point
 * @param end end point of the segment
 */
fun Segment3D(start: Vector3, c0: Vector3, end: Vector3) = Segment3D(start, listOf(c0), end)


/**
 * Cubic bezier segment constructor
 * @param start starting point of the segment
 * @param c0 first control point
 * @param c1 second control point
 * @param end end point of the segment
 */
fun Segment3D(start: Vector3, c0: Vector3, c1: Vector3, end: Vector3) = Segment3D(start, listOf(c0, c1), end)

