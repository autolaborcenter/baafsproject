package com.faselase

import cn.autolabor.serialport.manager.SerialPortManager
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.algebra.implement.matrix.builder.toDiagonalMatrix
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.geometry.transformation.Pose2D
import org.mechdancer.geometry.transformation.Transformation
import org.mechdancer.geometry.transformation.pose2D
import org.mechdancer.geometry.transformation.toTransformation

/**
 * 砝石雷达系构建器
 */
@BuilderDslMarker
class FaselaseLidarSetBuilderDsl private constructor() {
    var dataTimeout: Long = 400L

    private var autoDetect: FaselaseLidarConfig? = null
    private var configs = mutableMapOf<String?, FaselaseLidarConfig>()
    private var filter: (Vector2D) -> Boolean = { true }

    data class FaselaseLidarConfig internal constructor(
        var tag: String? = null,
        var pose: Pose2D = pose2D(),
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
        private val mirror = Transformation(listOf(+1, -1, +1).toDiagonalMatrix())

        fun SerialPortManager.registerFaselaseLidarSet(
            exceptions: SendChannel<ExceptionMessage>,
            block: FaselaseLidarSetBuilderDsl.() -> Unit
        ) = FaselaseLidarSetBuilderDsl()
            .apply(block)
            .apply {
                require(dataTimeout > 0)
                if (configs.isEmpty()) configs = mutableMapOf(null to autoDetect!!)
            }
            .run {
                val manager = this@registerFaselaseLidarSet
                var i = 0
                configs
                    .map { (portName, config) ->
                        val lidar =
                            FaselaseLidar(
                                exceptions = exceptions,
                                portName = portName,
                                tag = config.tag ?: "Lidar${i++}",
                                dataTimeout = dataTimeout
                            ).also(manager::register)
                        val tf = config.pose.toTransformation()
                        lidar to if (config.inverse) tf * mirror else tf
                    }
                    .let { LidarSet(it.associate { (lidar, tf) -> lidar::frame to tf }, filter) }
            }
    }
}
