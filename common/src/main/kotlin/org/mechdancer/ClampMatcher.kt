package org.mechdancer

import java.util.*
import java.util.concurrent.PriorityBlockingQueue

/**
 * 标准非阻塞比较匹配器
 *
 * @param T1 第一类对象
 * @param T2 第二类对象
 */
class ClampMatcher<T1, T2>(
    private val consume: Boolean
) : Matcher<T1, T2>
        where T1 : Comparable<T2>,
              T2 : Comparable<T1> {

    // 第一类对象队列
    private val queue1 = PriorityBlockingQueue<T1>()
    // 第二类对象队列
    private val queue2 = PriorityBlockingQueue<T2>()

    override fun add1(item: T1) {
        queue1.add(item)
    }

    override fun add2(item: T2) {
        queue2.add(item)
    }

    override fun match1() = match(queue1, queue2, consume)

    override fun match2() = match(queue2, queue1, consume)

    private companion object {
        /**
         * 匹配算法
         * @param queue1 基准对象队列
         * @param queue2 匹配对象队列
         */
        fun <T1, T2> match(
            queue1: Queue<T1>,
            queue2: Queue<T2>,
            consumeFrom1: Boolean
        ): Triple<T1, T2, T2>?
                where T1 : Comparable<T2>,
                      T2 : Comparable<T1> {
            while (true) {
                val a = queue1.peek() ?: return null
                val b = queue2.poll() ?: return null
                val c = queue2.peek() ?: run { queue2.offer(b); return null }
                when {
                    a < b -> {
                        queue2.offer(b)
                        queue1.remove(a)
                    }
                    a > c -> Unit
                    else  -> {
                        if (consumeFrom1) queue1.remove(a)
                        return Triple(a, b, c)
                    }
                }
            }
        }
    }
}
