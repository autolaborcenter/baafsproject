package com.faselase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.transformation.Transformation

class FaselaseLidarSetBuilderDsl private constructor() {
    var launchTimeout: Long = 5000L
    var connectionTimeout: Long = 3000L
    var dataTimeout: Long = 2000L
    var configs = mutableMapOf<String, FaselaseLidarConfig>()

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
            exceptions: SendChannel<ExceptionMessage>,
            block: FaselaseLidarSetBuilderDsl.() -> Unit
        ) = FaselaseLidarSetBuilderDsl()
            .apply(block)
            .run {
                configs
                    .takeIf(Map<*, *>::isNotEmpty)
                    ?.map { (portName, config) ->
                        FaselaseLidar(
                            scope = this@startFaselaseLidarSet,
                            exceptions = exceptions,
                            portName = portName,
                            tag = config.tag,
                            launchTimeout = launchTimeout,
                            connectionTimeout = connectionTimeout,
                            dataTimeout = dataTimeout
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
                    ?.let(::FaselaseLidarSet)
            }
    }
}
