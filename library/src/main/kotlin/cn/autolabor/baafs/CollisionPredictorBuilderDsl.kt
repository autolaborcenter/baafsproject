package cn.autolabor.baafs

import com.faselase.LidarSet
import org.mechdancer.BuilderDslMarker
import org.mechdancer.SimpleLogger
import org.mechdancer.common.shape.Polygon
import org.mechdancer.remote.presets.RemoteHub

@BuilderDslMarker
class CollisionPredictorBuilderDsl
private constructor() {
    var predictingTime: Long = 1000L
    var countToContinue: Int = 5
    var countToStop: Int = 5
    var logger: SimpleLogger? = SimpleLogger("CollisionPredictingModule")
    var painter: RemoteHub? = null

    companion object {
        fun collisionPredictor(
            lidarSet: LidarSet,
            robotOutline: Polygon,
            block: CollisionPredictorBuilderDsl.() -> Unit
        ) =
            CollisionPredictorBuilderDsl()
                .apply(block)
                .apply {
                    require(predictingTime > 0)
                    require(countToContinue >= 0)
                    require(countToStop >= 0)
                }
                .run {
                    CollisionPredictor(
                            lidarSet = lidarSet,
                            robotOutline = robotOutline,
                            predictingTime = predictingTime,
                            countToContinue = countToContinue,
                            countToStop = countToStop,
                            logger = logger,
                            painter = painter)
                }
    }
}
