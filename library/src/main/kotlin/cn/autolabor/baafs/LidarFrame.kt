package cn.autolabor.baafs

import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.function.vector.div
import org.mechdancer.algebra.function.vector.plus
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.listVectorOfZero
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.datagrid.DataGird
import org.mechdancer.datagrid.PlaneIndex

fun List<Vector2D>.toGridOf(block: Vector2D) =
    takeUnless(Collection<*>::isEmpty)
        ?.let { points ->
            DataGird(points) {
                PlaneIndex((it / block).toList().map(Number::toInt))
            }
        }
        ?.run {
            regionMap.regions
                .mapNotNull { region ->
                    region
                        .takeIf { it.size > 2 }
                        ?.map(grids::getValue)
                        ?.map { set ->
                            set.fold(listVectorOfZero(2))
                            { sum, it -> sum + it } / set.size
                        }
                }
        }
        ?.flatten()
        ?.map(Vector::to2D)
    ?: emptyList()
