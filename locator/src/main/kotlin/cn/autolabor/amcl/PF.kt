package cn.autolabor.amcl

import org.mechdancer.algebra.core.Matrix
import org.mechdancer.algebra.function.matrix.div
import org.mechdancer.algebra.function.matrix.plus
import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.function.matrix.toList
import org.mechdancer.algebra.implement.matrix.ArrayMatrix
import org.mechdancer.algebra.implement.matrix.builder.arrayMatrixOf
import org.mechdancer.algebra.implement.matrix.builder.arrayMatrixOfZero
import org.mechdancer.algebra.implement.vector.Vector3D
import kotlin.math.*

class PFInfo(
    val minSamples: Int = 500, val maxSamples: Int = 3000,
    val alphaSlow: Double = 0.001, val alphaFast: Double = 0.1,
    var wSlow: Double = 0.0, var wFast: Double = 0.0,
    var popErr: Double = 0.01, var popZ: Double = 3.0,
    var set: PFSampleSet = PFSampleSet(maxSamples),
    val distThreshold: Double = 0.2,
    var converged: Boolean = false)

fun Number.format(fmt: String): String =
    String.format(fmt, this)

fun Vector3D.format(fmt: String = "%+7.4f"): String =
    toList().joinToString(", ", "[", "]") { String.format(fmt, it) }

fun Matrix.format(fmt: String = "%+7.4f"): String =
    toList().joinToString(", ", "[", "]") { String.format(fmt, it) }

data class PFSample(var particle: Vector3D, var weight: Double)

infix fun Vector3D.to(that: Double) = PFSample(this, that)

class PFSampleSet(
    var samples: MutableList<PFSample> = mutableListOf(),
    var kdTree: KdTree,
    var maxClusterCount: Int,
    var clusters: MutableMap<Int, PFCluster> = mutableMapOf(),
    var mean: Vector3D = Vector3D(0.0, 0.0, 0.0),
    var cov: ArrayMatrix = arrayMatrixOfZero(3),
    var converged: Boolean = false
) {
    constructor(count: Int) : this(
            maxClusterCount = count,
            kdTree = KdTree(nodeMaxCount = 3 * count))

    override fun toString() = buildString {
        appendln("MEAN : ${mean.format()}")
        appendln("COV: ${cov.format()}")
        appendln("SET(${samples.size}) :")
        this@PFSampleSet.samples
            .toList()
            .sortedByDescending { it.weight }
            .take(5)
            .forEach { (particle, weight) -> appendln("$weight -> ${particle.format()}") }
        appendln("CLUSTER(${clusters.size}) :")
        this@PFSampleSet.clusters
            .toList()
            .sortedByDescending { it.second.count }
            .take(5)
            .forEach { (id, cluster) -> appendln("${id.format("%-4d")} -> $cluster") }
    }
}

class PFCluster(
    var count: Int = 0,
    var weight: Double = 0.0,
    var mean: Vector3D = Vector3D(0.0, 0.0, 0.0),
    var cov: Matrix = arrayMatrixOfZero(3),
    var m: Matrix = arrayMatrixOfZero(1, 4),
    var c: Matrix = arrayMatrixOfZero(2)
) {
    override fun toString() =
        "PFCluster(count: ${count.format("%-3d")} " +
        "weight: ${weight.format("%4.2f")}   " +
        "mean: ${mean.format()}   " +
        "cov: ${cov.format()}   " +
        "m: ${m.format()}" + ")"
}

fun pose2matrix(pose: Vector3D): Matrix =
    arrayMatrixOf(1, 4) { _, i ->
        when (i) {
            0    -> pose.x
            1    -> pose.y
            2    -> cos(pose.z)
            3    -> sin(pose.z)
            else -> 0
        }
    }

fun matrix2pose(m: Matrix) =
    Vector3D(x = m[0, 0],
             y = m[0, 1],
             z = atan2(m[0, 3], m[0, 2]))

fun clusterStats(pf: PFInfo) {
    var count = 0
    var weight = .0
    var m: Matrix = arrayMatrixOfZero(1, 4)
    var c: Matrix = arrayMatrixOfZero(2)

    kdTreeCluster(pf.set.kdTree)
    pf.set.clusters.clear()

    for (sample: PFSample in pf.set.samples)
        kdTreeGetCluster(pf.set.kdTree, sample.particle)
            .takeIf { it <= pf.set.maxClusterCount }
            ?.apply {
                pf.set.clusters.getOrPut(this) { PFCluster() }
                    .also { cluster ->
                        ++cluster.count
                        count += cluster.count
                        sample.weight.apply { cluster.weight += this }.also { weight += it }
                        (pose2matrix(sample.particle) * sample.weight)
                            .apply { cluster.m += this }.also { m += it }
                        arrayMatrixOf(2, 2) { i, j -> sample.weight * sample.particle[i] * sample.particle[j] }
                            .apply { cluster.c += this }.also { c += it }
                    }
            }

    for ((_, cluster) in pf.set.clusters) {
        cluster.mean = matrix2pose(cluster.m / cluster.weight)
        cluster.cov = arrayMatrixOf(3, 3) { i, j ->
            when {
                i in 0..1 && j in 0..1 -> cluster.c[i, j] / cluster.weight - cluster.mean[i] * cluster.mean[j]
                i == 2 && j == 2       -> -2.0 * ln(sqrt(cluster.m[0, 2] * cluster.m[0, 2] + cluster.m[0, 3] * cluster.m[0, 3]))
                else                   -> 0
            }
        }
    }

    pf.set.mean = matrix2pose(m / weight)
    pf.set.cov = arrayMatrixOf(3, 3) { i, j ->
        when {
            i in 0..1 && j in 0..1 -> c[i, j] / weight - pf.set.mean[i] * pf.set.mean[j]
            i == 2 && j == 2       -> -2.0 * ln(sqrt(m[0, 2] * m[0, 2] + m[0, 3] * m[0, 3]))
            else                   -> 0
        }
    }
}
