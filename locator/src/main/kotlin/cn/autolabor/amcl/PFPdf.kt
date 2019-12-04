package cn.autolabor.amcl

import org.mechdancer.algebra.function.matrix.times
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.matrix.ArrayMatrix
import org.mechdancer.algebra.implement.matrix.builder.arrayMatrixOf
import org.mechdancer.algebra.implement.matrix.builder.arrayMatrixOfZero
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.matrix.builder.toArrayMatrix
import org.mechdancer.algebra.implement.vector.Vector3D
import org.mechdancer.algebra.implement.vector.vector3DOf
import kotlin.math.*
import kotlin.random.Random

data class Pdf(
    var x: Vector3D = vector3DOf(0, 0, 0),
    var cx: ArrayMatrix = arrayMatrixOfZero(3),
    var cr: ArrayMatrix = arrayMatrixOfZero(3),
    var cd: ArrayMatrix = arrayMatrixOfZero(3, 1)
) {
    constructor(mean: Vector3D, cov: ArrayMatrix)
            : this(x = mean, cx = cov) {
        matrixUnitary(cr, cd, cx)
    }
}

fun ArrayMatrix.toVec3D() =
    Vector3D(x = this.data[0],
             y = this.data[1],
             z = this.data[2])

fun gaussianSample(pdf: Pdf): Vector3D {
    val noise = arrayMatrixOf(3, 1) { i, _ ->
        randomGaussian(pdf.cd.data[i])
    }
    return pdf.x + (pdf.cr * noise).toArrayMatrix().toVec3D()
}

fun ArrayMatrix.clear() {
    for (i in 0 until this.row)
        for (j in 0 until this.column) {
            this[i, j] = 0.0
        }
}

fun matrixUnitary(r: ArrayMatrix, d: ArrayMatrix, a: ArrayMatrix) {
    val aa: ArrayMatrix = arrayMatrixOf(3, 3) { i, j -> a[i, j] }
    eigenDecomposition(aa, r, d)
}

fun tred2(v: ArrayMatrix, d: ArrayMatrix, e: ArrayMatrix): Unit {
    val n = v.row
    var f: Double
    var g: Double
    var h: Double
    var hh: Double
    repeat(n) { d.data[it] = v[n - 1, it] }
    // Householder reduction to tridiagonal form.
    for (i in n - 1 downTo 1) {
        // println("[$i]->v : ${v.format()}")
        val scale = d.data.take(i).sumByDouble(::abs)
        var h = 0.0
        if (scale == 0.0) {
            e.data[i] = d.data[i - 1]
            for (j in 0 until i) {
                d.data[j] = v[i - 1, j]
                v[i, j] = 0.0
                v[j, i] = 0.0
            }
        } else {
            // Generate Householder vector.
            for (k in 0 until i) {
                d.data[k] /= scale
                h += d.data[k] * d.data[k]
            }

            f = d.data[i - 1]
            g = if (f > 0) -sqrt(h) else sqrt(h)
            e.data[i] = scale * g
            h -= f * g
            d.data[i - 1] = f - g
            for (j in 0 until i) {
                e.data[j] = 0.0
            }
            // Apply similarity transformation to remaining columns.
            for (j in 0 until i) {
                f = d.data[j]
                v[j, i] = f
                g = e.data[j] + v[j, j] * f
                for (k in (j + 1) until i) {
                    g += v[k, j] * d.data[k]
                    e.data[k] += v[k, j] * f
                }
                e.data[j] = g
            }
            f = 0.0
            // h == 0
            for (j in 0 until i) {
                e.data[j] /= h
                f += e.data[j] * d.data[j]
            }
            hh = f / (h + h)
            for (j in 0 until i) {
                e.data[j] -= hh * d.data[j]
            }
            for (j in 0 until i) {
                for (k in j until i) {
                    v[k, j] -= (d.data[j] * e.data[k] + e.data[j] * d.data[k])
                }
                d.data[j] = v[i - 1, j]
                v[i, j] = 0.0
            }
        }
        d.data[i] = h
    }
    // Accumulate transformations.
    for (i in 0 until n - 1) {
        v[n - 1, i] = v[i, i]
        v[i, i] = 1.0
        h = d.data[i + 1]
        if (h != 0.0) {
            for (k in 0..i) {
                d.data[k] = v[k, i + 1] / h
            }
            for (j in 0..i) {
                g = 0.0
                for (k in 0..i) {
                    g += v[k, i + 1] * v[k, j]
                }
                for (k in 0..i) {
                    v[k, j] -= g * d.data[k]
                }
            }
        }
        for (k in 0..i) {
            v[k, i + 1] = 0.0
        }
    }
    for (j in 0 until n) {
        d.data[j] = v[n - 1, j]
        v[n - 1, j] = 0.0
    }
    v[n - 1, n - 1] = 1.0
    e.data[0] = 0.0
}

fun hypot2(x: Double, y: Double): Double {
    return sqrt(x * x + y * y)
}

fun tql2(v: ArrayMatrix, d: ArrayMatrix, e: ArrayMatrix): Unit {
    var f = 0.0
    var tst1 = 0.0
    val eps = 2.0.pow(-52.0)
    var g: Double
    var p: Double
    var r: Double
    var dl1: Double
    var h: Double
    var c: Double
    var c2: Double
    var c3: Double
    var el1: Double
    var s: Double
    var s2: Double
    val n = v.row
    for (i in 1 until n) {
        e.data[i - 1] = e.data[i]
    }
    e.data[n - 1] = 0.0

    for (l in 0 until n) {

        // Find small subdiagonal element

        tst1 = max(tst1, abs(d.data[l]) + abs(e.data[l]))
        var m = l
        while (m < n) {
            if (abs(e.data[m]) <= eps * tst1) {
                break
            }
            m++
        }

        // If m == l, d.data[l] is an eigenvalue,
        // otherwise, iterate.

        if (m > l) {
            var iter = 0
            do {
                iter += 1  // (Could check iteration count here.)

                // Compute implicit shift

                g = d.data[l]
                p = (d.data[l + 1] - g) / (2.0 * e.data[l])
                r = hypot2(p, 1.0)
                if (p < 0) {
                    r = -r
                }
                d.data[l] = e.data[l] / (p + r)
                d.data[l + 1] = e.data[l] * (p + r)
                dl1 = d.data[l + 1]
                h = g - d.data[l]
                for (i in l + 2 until n) {
                    d.data[i] -= h
                }
                f += h

                // Implicit QL transformation.
                p = d.data[m]
                c = 1.0
                c2 = c
                c3 = c
                el1 = e.data[l + 1]
                s = 0.0
                s2 = 0.0
                for (i in m - 1 downTo l) {
                    c3 = c2
                    c2 = c
                    s2 = s
                    g = c * e.data[i]
                    h = c * p
                    r = hypot2(p, e.data[i])
                    e.data[i + 1] = s * r
                    s = e.data[i] / r
                    c = p / r
                    p = c * d.data[i] - s * g
                    d.data[i + 1] = h + s * (c * g + s * d.data[i])

                    // Accumulate transformation.

                    for (k in 0 until n) {
                        h = v[k, i + 1]
                        v[k, i + 1] = s * v[k, i] + c * h
                        v[k, i] = c * v[k, i] - s * h
                    }
                }
                p = -s * s2 * c3 * el1 * e.data[l] / dl1
                e.data[l] = s * p
                d.data[l] = c * p

                // Check for convergence.

            } while (abs(e.data[l]) > eps * tst1)
        }
        d.data[l] = d.data[l] + f
        e.data[l] = 0.0
    }
}

fun eigenDecomposition(a: ArrayMatrix, v: ArrayMatrix, d: ArrayMatrix) {
    val n = a.row
    val e = arrayMatrixOfZero(n, 1)
    a.data.forEachIndexed { index, value -> v.data[index] = value }
    // println("F : v-> ${v.format()} d -> ${d.format()}, e -> ${e.format()}")
    tred2(v, d, e)
    // println("M : v-> ${v.format()} d -> ${d.format()}, e -> ${e.format()}")
    tql2(v, d, e)
    // println("B : v-> ${v.format()} d -> ${d.format()}, e -> ${e.format()}")
}

fun randomGaussian(sigma: Double): Double {
    var x1: Double
    var x2: Double
    var w: Double
    var r: Double

    do {
        do {
            r = Random.nextDouble()
        } while (r == 0.0)
        x1 = 2.0 * r - 1.0
        do {
            r = Random.nextDouble()
        } while (r == 0.0)
        x2 = 2.0 * r - 1.0
        w = x1 * x1 + x2 * x2
    } while (w > 1.0 || w == 0.0)
    return (sigma * x2 * sqrt(-2.0 * ln(w) / w))
}

fun randomGaussian(mean: Double, sigma: Double): Double {
    return randomGaussian(sigma) + mean
}

fun main() {
    val a = matrix {
        row(1, 4, 0)
        row(4, 3, 0)
        row(0, 0, 5)
    }.toArrayMatrix()
    val v = arrayMatrixOfZero(3, 3)
    val d = arrayMatrixOfZero(3, 1)
    eigenDecomposition(a, v, d)
    println(d)
    print(v)
    print(a * v.column(0) / v.column(0))
    print(a * v.column(1) / v.column(1))
    print(a * v.column(2) / v.column(2))
}
