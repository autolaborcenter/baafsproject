package cn.autolabor.pm1

import cn.autolabor.autocan.PM1Pack
import cn.autolabor.autocan.engine
import org.mechdancer.common.Stamped

class Chassis {
    private val engine = engine()
    private var left = Stamped(0L, .0)
    private var right = Stamped(0L, .0)
    private var rudder = Stamped(0L, .0)

    // 定时发送
    // 接收左右轮时更新里程计
    // 接收后轮时发送控制指令

    fun parse(bytes: List<Byte>) {
        engine(bytes) { pack ->
            val now = System.currentTimeMillis()
            when (pack) {
                PM1Pack.Nothing,
                PM1Pack.Failed,
                is PM1Pack.WithoutData -> Unit
                is PM1Pack.WithData    ->
                    when (pack.head) {
                        CanNode.ECU(0).currentPositionRx ->
                            left = Stamped(now, .0)
                        CanNode.ECU(1).currentPositionRx ->
                            right = Stamped(now, .0)
                        CanNode.TCU(0).currentPositionRx ->
                            rudder = Stamped(now, .0)
                    }
            }
        }
    }
}
