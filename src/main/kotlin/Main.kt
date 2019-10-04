import AutolaborScript.ScriptConfiguration
import java.io.File
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmEvaluationConfigurationFromTemplate

fun main(args: Array<String>) {
    // 查找脚本文件
    val fileName = args.lastOrNull()?.takeUnless { it.startsWith("-") } ?: "default.autolabor.kts"
    val file = File(fileName)
    if (!file.exists()) {
        System.err.println("Cannot find script. Please Check your path: ${file.absolutePath}.")
        return
    }
    println("compiling...")
    BasicJvmScriptingHost()
        .eval(file.toScriptSource(),
              ScriptConfiguration,
              createJvmEvaluationConfigurationFromTemplate<AutolaborScript>())
        .run {
            if ("-debugScript" in args) println(reports)
        }
}

//package org.mechdancer.baafs
//
//import cn.autolabor.locator.LocationFusionModuleBuilderDsl.Companion.startLocationFusion
//import cn.autolabor.pathfollower.PathFollowerModuleBuilderDsl.Companion.startPathFollower
//import kotlinx.coroutines.ExperimentalCoroutinesApi
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.runBlocking
//import org.mechdancer.algebra.implement.vector.Vector2D
//import org.mechdancer.algebra.implement.vector.vector2DOf
//import org.mechdancer.baafs.modules.LinkMode.Direct
//import org.mechdancer.baafs.modules.startBeacon
//import org.mechdancer.baafs.modules.startChassis
//import org.mechdancer.baafs.modules.startObstacleAvoiding
//import org.mechdancer.channel
//import org.mechdancer.common.Odometry
//import org.mechdancer.common.Stamped
//import org.mechdancer.common.Velocity.NonOmnidirectional
//
//@ExperimentalCoroutinesApi
//fun main() {
//    val mode = Direct
//    // 话题
//    val robotOnOdometry = channel<Stamped<Odometry>>()
//    val robotOnMap = channel<Stamped<Odometry>>()
//    val beaconOnMap = channel<Stamped<Vector2D>>()
//    val commandToObstacle = channel<NonOmnidirectional>()
//    val commandToRobot = channel<NonOmnidirectional>()
//    // 任务
//    try {
//        runBlocking {
//            startChassis(
//                mode = mode,
//                odometry = robotOnOdometry,
//                command = commandToRobot)
//            startBeacon(
//                mode = mode,
//                beaconOnMap = beaconOnMap)
//            startLocationFusion(
//                robotOnOdometry = robotOnOdometry,
//                beaconOnMap = beaconOnMap,
//                robotOnMap = robotOnMap) {
//                filter {
//                    beaconOnRobot = vector2DOf(-0.37, 0)
//                }
//            }
//            startPathFollower(
//                robotOnMap = robotOnMap,
//                commandOut = commandToObstacle)
//            startObstacleAvoiding(
//                mode = mode,
//                commandIn = commandToObstacle,
//                commandOut = commandToRobot)
//            coroutineContext[Job]
//                ?.children
//                ?.filter { it.isActive }
//                ?.toList()
//                ?.run { println("running coroutines: $size") }
//        }
//    } catch (e: Exception) {
//        System.err.println("program stop with ${e::class.simpleName}: ${e.message}")
//    }
//}
