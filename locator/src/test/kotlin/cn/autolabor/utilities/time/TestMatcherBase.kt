package cn.autolabor.utilities.time

import kotlin.concurrent.thread
import kotlin.test.Test

class TestMatcherBase {
    // 测试参数
    private companion object {
        const val interval1 = 29L
        const val interval2 = 17L

        val range = (interval2 - 5)..(interval2 + 5)
    }

    @Test
    fun test() {
        val matcher = MatcherBase<Stamped<Int>, Stamped<Int>>()

        val addTask = { intervalMs: Long, block: Matcher<Stamped<Int>, Stamped<Int>>.() -> Unit ->
            while (true) {
                synchronized(matcher) { matcher.block() }
                Thread.sleep(intervalMs)
            }
        }

        var a = 0
        var b = 0

        thread { addTask(interval1) { add1(Stamped(System.currentTimeMillis(), ++a)) } }
        thread { addTask(interval2) { add2(Stamped(System.currentTimeMillis(), ++b)) } }

        val t0 = System.currentTimeMillis()
        var x = 0
        while (true) {
            val t1 = System.currentTimeMillis()
            synchronized(matcher) {
                matcher.match1()?.let { (item, before, after) ->
                    assert((after.time - before.time) in range) {
                        "匹配项时间间隔不正常：${after.time - before.time} ms"
                    }
                    assert((after.data - before.data) == 1) {
                        "匹配项不正常：${after.data - before.data} ms"
                    }
                    assert(item.data - x == 1) {
                        "匹配项丢失：$x -> ${item.data}"
                    }
                    x = item.data
                }
            }
            if (t1 - t0 > 5000) break
        }
    }
}

