package cn.autolabor.pathfollower.algorithm

/**
 * 控制器
 */
interface Controller {
    operator fun invoke(time: Long? = null, input: Double): Double

    fun clear() {}

    companion object {
        val unit = object : Controller {
            override fun invoke(time: Long?, input: Double) = input
        }
    }
}
