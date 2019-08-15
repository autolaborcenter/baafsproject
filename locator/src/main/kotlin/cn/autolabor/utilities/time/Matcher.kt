package cn.autolabor.utilities.time

/**
 * 匹配器
 *
 * @param T1 第一类对象
 * @param T2 第二类对象
 */
interface Matcher<
    T1 : Comparable<T2>,
    T2 : Comparable<T1>> {
    /**
     * 添加可用于匹配的第一类对象
     */
    fun add1(item: T1)

    /**
     * 添加可用于匹配的第二类对象
     */
    fun add2(item: T2)

    /**
     * 以第一类对象为基准匹配
     */
    fun match1(): Triple<T1, T2, T2>?

    /**
     * 以第二类对象为基准匹配
     */
    fun match2(): Triple<T2, T1, T1>?
}
