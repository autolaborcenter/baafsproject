package cn.autolabor.time

import java.util.*

/**
 * 标准非阻塞比较匹配器
 */
class MatcherBase<T1 : Comparable<T2>, T2 : Comparable<T1>>
    : Matcher<T1, T2> {
    private val queue1 = PriorityQueue<T1>()
    private val queue2 = PriorityQueue<T2>()

    override fun add1(item: T1) {
        queue1.add(item)
    }

    override fun add2(item: T2) {
        queue2.add(item)
    }

    override fun match1() = match(queue1, queue2)

    override fun match2() = match(queue2, queue1)

    private companion object {
        fun <T1 : Comparable<T2>, T2 : Comparable<T1>>
            match(queue1: Queue<T1>, queue2: Queue<T2>)
            : Triple<T1, T2, T2>? {
            while (queue1.isNotEmpty() && queue2.size > 1) {
                val a = queue1.peek()
                val b = queue2.poll()
                val c = queue2.peek()
                when {
                    a < b -> {
                        queue2.offer(b)
                        queue1.poll()
                    }
                    a > c -> Unit
                    else  -> return Triple(queue1.poll(), b, c)
                }
            }
            return null
        }
    }
}
