package cn.autolabor.utilities.time

import java.util.*

/**
 * 标准非阻塞比较匹配器
 *
 * @param T1 第一类对象
 * @param T2 第二类对象
 */
class MatcherBase<T1, T2> : Matcher<T1, T2>
    where T1 : Comparable<T2>,
          T2 : Comparable<T1> {

    // 第一类对象队列
    private val queue1 = PriorityQueue<T1>()
    // 第二类对象队列
    private val queue2 = PriorityQueue<T2>()

    override fun add1(item: T1) {
        synchronized(queue1) { queue1.add(item) }
    }

    override fun add2(item: T2) {
        synchronized(queue2) { queue2.add(item) }
    }

    override fun match1() = match(queue1, queue2)

    override fun match2() = match(queue2, queue1)

    private companion object {
        /**
         * 非对等匹配算法
         * @param queue1 基准对象队列
         * @param queue2 匹配对象队列
         */
        fun <T1, T2> match(
            queue1: Queue<T1>,
            queue2: Queue<T2>
        ): Triple<T1, T2, T2>?
            where T1 : Comparable<T2>,
                  T2 : Comparable<T1> {
            synchronized(queue1) {
                synchronized(queue2) {
                    while (true) {
                        val a = queue1.peek() ?: return null
                        val b = queue2.poll() ?: return null
                        val c = queue2.peek() ?: return null
                        when {
                            a < b -> {
                                queue2.offer(b)
                                queue1.poll()
                            }
                            a > c -> Unit
                            else  -> return Triple(queue1.poll(), b, c)
                        }
                    }
                }
            }
        }
    }
}
