package com.faselase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mechdancer.algebra.core.Vector
import org.mechdancer.algebra.implement.matrix.builder.matrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.to2D
import org.mechdancer.common.Odometry
import org.mechdancer.common.toTransformation
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.transformation.Transformation

class FaselaseLidarSetBuilderDsl private constructor() {
    var launchTimeout: Long = 5000L
    var connectionTimeout: Long = 3000L
    var dataTimeout: Long = 2000L
    var retryInterval: Long = 100L
    var period: Long = 100L

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
        fun CoroutineScope.startFaselaseLidarSet(
            points: SendChannel<List<Vector2D>>,
            exceptions: SendChannel<ExceptionMessage>,
            block: FaselaseLidarSetBuilderDsl.() -> Unit
        ) {
            FaselaseLidarSetBuilderDsl()
                .apply(block)
                .apply {
                    if (configs.isEmpty())
                        configs = mutableMapOf(null to autoDetect!!)
                }
                .run {
                    val map = configs.map { (portName, config) ->
                        FaselaseLidar(
                            scope = this@startFaselaseLidarSet,
                            exceptions = exceptions,
                            portName = portName,
                            tag = config.tag,
                            launchTimeout = launchTimeout,
                            connectionTimeout = connectionTimeout,
                            dataTimeout = dataTimeout,
                            retryInterval = retryInterval
                        ) to config.pose.toTransformation().let {
                            if (config.inverse)
                                it * Transformation(matrix {
                                    row(+1, 0, 0)
                                    row(0, -1, 0)
                                    row(0, 0, +1)
                                })
                            else it
                        }
                    }.toMap()

                    launch {
                        while (true) {
                            map.asSequence()
                                .flatMap { (lidar, toRobot) ->
                                    lidar.frame
                                        .asSequence()
                                        .map { (_, polar) -> toRobot(polar.toVector2D()) }
                                }
                                .map(Vector::to2D)
                                .filter(filter)
                                .toList()
                                .let { points.send(it) }
                            delay(period)
                        }
                    }.invokeOnCompletion {
                        points.close()
                    }
                }
        }
    }
}
