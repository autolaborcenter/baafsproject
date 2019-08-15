import cn.autolabor.time.MatcherBase
import cn.autolabor.time.Stamped
import kotlin.concurrent.thread

fun main() {
    val matcher = MatcherBase<Stamped<Unit>, Stamped<Unit>>()

    thread {
        while (true) {
            synchronized(matcher) {
                matcher.add1(Stamped(System.currentTimeMillis(), Unit))
                matcher.match1()
                    ?.also { (a, b, c) ->
                        println("${a.time - b.time} ${c.time - a.time}")
                    }
            }
            Thread.sleep(100)
        }
    }

    while (true) {
        synchronized(matcher) {
            matcher.add2(Stamped(System.currentTimeMillis(), Unit))
        }
        Thread.sleep(200)
    }
}
