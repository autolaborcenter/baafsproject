package cn.autolabor.utilities.time

/**
 * 将 [T1] 对象与 [T2] 对象按顺序匹配的匹配器
 * 要求 [T1] 和 [T2] 相互可比
 */
interface Matcher<T1, T2>
    where T1 : Comparable<T2>,
          T2 : Comparable<T1>,
          T1 : Any,
          T2 : Any {
    /** 添加用于匹配的 [T1] 对象 */
    fun add1(item: T1)

    /** 添加用于匹配的 [T2] 对象 */
    fun add2(item: T2)

    /** 以 [T1] 对象为基准匹配 */
    fun match1(): Triple<T1, T2, T2>?

    /** 以 [T2] 对象为基准匹配 */
    fun match2(): Triple<T2, T1, T1>?
}
