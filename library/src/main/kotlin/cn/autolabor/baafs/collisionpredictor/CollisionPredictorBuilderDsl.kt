package cn.autolabor.baafs.collisionpredictor

import org.mechdancer.SimpleLogger
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.annotations.BuilderDslMarker
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

    private var obstacleSource: suspend () -> Collection<Vector2D> =
        { emptyList() }

    fun obstacles(block: suspend () -> Collection<Vector2D>) {
        obstacleSource = block
    }

    companion object {
        fun collisionPredictor(
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
                            robotOutline = robotOutline,
                            predictingTime = predictingTime,
                            countToContinue = countToContinue,
                            countToStop = countToStop,
                            obstacleSource = obstacleSource)
                        .also {
                            it.logger = logger
                            it.painter = painter
                        }
                }
    }
}
