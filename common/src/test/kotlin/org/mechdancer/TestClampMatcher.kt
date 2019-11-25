package org.mechdancer

import org.mechdancer.common.Stamped
import kotlin.concurrent.thread
import kotlin.test.Test

class TestClampMatcher {
    // 测试参数
    private companion object {
        const val interval1 = 79L
        const val interval2 = 67L

        val range = (interval2 - 10)..(interval2 + 10)
    }

    @Test
    fun test() {
        val matcher = ClampMatcher<Stamped<Int>, Stamped<Int>>(true)

        val addTask = { intervalMs: Long, block: Matcher<Stamped<Int>, Stamped<Int>>.() -> Unit ->
            while (true) {
                synchronized(matcher) { matcher.block() }
                Thread.sleep(intervalMs)
            }
        }

        var a = 0
        var b = 0

        thread { addTask(interval1) { add1(Stamped.stamp(++a)) } }
        thread { addTask(interval2) { add1(Stamped.stamp(++b)) } }

        val t0 = System.currentTimeMillis()
        var x = 0
        var i = 0
        while (true) {
            val t1 = System.currentTimeMillis()
            synchronized(matcher) {
                matcher.match1()?.let { (item, before, after) ->
                    ++i
                    assert((after.time - before.time) in range) {
                        "匹配项时间间隔不正常：$i: ${after.time - before.time} ms"
                    }
                    assert((after.data - before.data) == 1) {
                        "匹配项不正常：$i: ${after.data - before.data} ms"
                    }
                    assert(item.data - x == 1) {
                        "匹配项丢失：$i: $x -> ${item.data}"
                    }
                    x = item.data
                }
            }
            if (t1 - t0 > 10000) break
        }
    }
}

