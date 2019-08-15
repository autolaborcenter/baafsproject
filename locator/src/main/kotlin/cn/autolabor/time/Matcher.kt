package cn.autolabor.time

/**
 * 匹配器
 */
interface Matcher<
    T1 : Comparable<T2>,
    T2 : Comparable<T1>> {
    fun add1(item: T1)
    fun add2(item: T2)
    fun match1(): Triple<T1, T2, T2>?
    fun match2(): Triple<T2, T1, T1>?
}
