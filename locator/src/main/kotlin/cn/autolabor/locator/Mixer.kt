package cn.autolabor.locator

/**
 * 融合器
 * @param TMaster 测量信息类型
 * @param THelper 校准信息类型
 * @param TResult 融合结果类型
 */
interface Mixer<TMaster, THelper, TResult> {
    /** 添加测量信息 */
    fun measureMaster(item: TMaster)

    /** 添加校准信息 */
    fun measureHelper(item: THelper)

    /** 从任意测量信息获取实际值 */
    operator fun get(item: TMaster): TResult?
}
