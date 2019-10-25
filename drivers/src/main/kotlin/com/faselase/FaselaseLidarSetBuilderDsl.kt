package com.faselase

import kotlinx.coroutines.CoroutineScope
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.transformation.Transformation
import org.mechdancer.paint
import org.mechdancer.remote.presets.RemoteHub

class FaselaseLidarSetBuilderDsl private constructor() {
    var connectionTimeout: Long = 5000L
    var configs = mutableMapOf<String, FaselaseLidarConfig>()
    var painter: RemoteHub? = null

    data class FaselaseLidarConfig internal constructor(
        var tag: String? = null,
        var pose: Odometry = Odometry(),
        var inverse: Boolean = false)

    fun lidar(
        port: String,
        block: FaselaseLidarConfig.() -> Unit
    ) = require(configs.putIfAbsent(port, FaselaseLidarConfig().apply(block)) == null) {
        "faselase lidar on $port is already added"
    }

    companion object {
        fun CoroutineScope.startFaselaseLidarSet(
            block: FaselaseLidarSetBuilderDsl.() -> Unit
        ) = FaselaseLidarSetBuilderDsl()
            .apply(block)
            .run {
                configs
                    .takeIf(Map<*, *>::isNotEmpty)
                    ?.map { (portName, config) ->
                        FaselaseLidar(
                            scope = this@startFaselaseLidarSet,
                            portName = portName,
                            tag = config.tag,
                            connectionTimeout = connectionTimeout
                        ) to config.pose.toTransformation().let {
                            if (config.inverse)
                                it * Transformation(matrix {
                                    row(+1, 0, 0)
                                    row(0, -1, 0)
                                    row(0, 0, +1)
                                })
                            else it
                        }
                    }
                    ?.toMap()
                    ?.onEach { (lidar, tf) ->
                        painter?.let {
                            synchronized(lidar.callbacks) {
                                lidar.callbacks += { (_, data) ->
                                    val v = tf(data.toVector2D()).to2D()
                                    it.paint("雷达数据", v.x, v.y)
                                }
                            }
                        }
                    }
                    ?.let(::FaselaseLidarSet)
            }
    }
}
