package com.faselase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.implement.matrix.builder.toDiagonalMatrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.transformation.Transformation

class FaselaseLidarSetBuilderDsl private constructor() {
    var launchTimeout: Long = 5000L
    var connectionTimeout: Long = 800L
    var dataTimeout: Long = 400L
    var retryInterval: Long = 100L

    private var autoDetect: FaselaseLidarConfig? = null
    private var configs = mutableMapOf<String?, FaselaseLidarConfig>()
    private var filter: (Vector2D) -> Boolean = { true }

    data class FaselaseLidarConfig internal constructor(
        var tag: String? = null,
        var pose: Odometry = Odometry(),
        var inverse: Boolean = false)

    fun lidar(block: FaselaseLidarConfig.() -> Unit) {
        require(configs.isEmpty())
        autoDetect = FaselaseLidarConfig().apply(block)
    }

    fun lidar(port: String,
              block: FaselaseLidarConfig.() -> Unit) {
        require(autoDetect == null)
        require(configs.putIfAbsent(port, FaselaseLidarConfig().apply(block)) == null) {
            "faselase lidar on $port is already added"
        }
    }

    fun filter(block: (Vector2D) -> Boolean) {
        filter = block
    }

    companion object {
        fun CoroutineScope.faselaseLidarSet(
            exceptions: SendChannel<ExceptionMessage>,
            block: FaselaseLidarSetBuilderDsl.() -> Unit
        ) = FaselaseLidarSetBuilderDsl()
            .apply(block)
            .apply {
                require(launchTimeout > 0)
                require(connectionTimeout > 0)
                require(dataTimeout > 0)
                require(retryInterval > 0)
                if (configs.isEmpty()) configs = mutableMapOf(null to autoDetect!!)
            }
            .run {
                configs.map { (portName, config) ->
                    FaselaseLidar(
                        scope = this@faselaseLidarSet,
                        exceptions = exceptions,
                        name = portName,
                        tag = config.tag,
                        launchTimeout = launchTimeout,
                        connectionTimeout = connectionTimeout,
                        dataTimeout = dataTimeout,
                        retryInterval = retryInterval
                    ) to config.pose.toTransformation().let {
                        if (config.inverse)
                            it * Transformation(listOf(+1, -1, +1).toDiagonalMatrix())
                        else it
                    }
                }.let { FaselaseLidarSet(it.toMap(), filter) }
            }
    }
}
