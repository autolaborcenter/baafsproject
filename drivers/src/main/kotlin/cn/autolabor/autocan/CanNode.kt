package cn.autolabor.autocan

internal sealed class CanNode(val type: Byte, val index: Byte) {
    // 状态
    val stateTx = dialogTx(0x80)
    val stateRx = dialogRx(0x80)
    // 版本 id
    val versionIdTx = dialogTx(0x81)
    val versionIdRx = dialogRx(0x81)
    // 设备 id
    val deviceIdTx = dialogTx(0x82)
    val deviceIdRx = dialogRx(0x82)
    // 芯片 id
    val chipIdTx = dialogTx(0x83)
    val chipIdRx = dialogRx(0x83)
    // HAL 版本
    val halVersionTx = dialogTx(0x84)
    val halVersionRx = dialogRx(0x84)
    // 核心板硬件版本
    val coreHardwareVersionIdTx = dialogTx(0x85)
    val coreHardwareVersionIdRx = dialogRx(0x85)
    // 扩展板硬件版本
    val extraHardwareVersionIdTx = dialogTx(0x86)
    val extraHardwareVersionIdRx = dialogRx(0x86)
    // 软件版本
    val softwareVersionIdTx = dialogTx(0x87)
    val softwareVersionIdRx = dialogRx(0x87)
    // 累计运行时间
    val uptimeTx = dialogRx(0x88)
    val uptimeRx = dialogRx(0x88)
    // 紧急停止
    val emergencyStop = signal(0xff)
    val releaseStop = message(0xff)

    /** 对所有节点广播 */
    object EveryNode : CanNode(0x3f, 0x0f)

    /** 整车控制器 */
    class VCU(index: Byte = EveryNode.index) : CanNode(0x10, index) {
        // 电池
        val batteryPercentTx = dialogTx(1)
        val batteryPercentRx = dialogRx(1)
        val batteryTimeTx = dialogTx(2)
        val batteryTimeRx = dialogRx(2)
        val batteryQuantityTx = dialogTx(3)
        val batteryQuantityRx = dialogRx(3)
        val batteryVoltageTx = dialogTx(4)
        val batteryVoltageRx = dialogRx(4)
        val batteryCurrentTx = dialogTx(5)
        val batteryCurrentRx = dialogRx(5)
        // 手柄
        val controlPadTx = dialogTx(6)
        val controlPadRx = dialogRx(6)
        // 急停开关
        val powerSwitchTx = dialogTx(7)
        val powerSwitchRx = dialogRx(7)
    }

    /** 动力控制器 */
    class ECU(index: Byte = EveryNode.index) : CanNode(0x11, index) {
        // 设置目标速度
        val targetSpeed = message(1)
        // 当前速度
        val currentSpeedTx = dialogTx(5)
        val currentSpeedRx = dialogRx(5)
        // 当前编码器读数
        val currentPositionTx = dialogTx(6)
        val currentPositionRx = dialogRx(6)
        // 编码器清零
        val encoderReset = signal(7)
        // 设置超时时间
        val timeout = message(0xa)
    }

    /** 转向控制器 */
    class TCU(index: Byte = EveryNode.index) : CanNode(0x12, index) {
        // 设置目标角度
        val targetPosition = message(1)
        // 设置目标角度增量
        val targetIncremental = message(2)
        // 当前角度
        val currentPositionTx = dialogTx(3)
        val currentPositionRx = dialogRx(3)
        // 设置目标角速度
        val targetSpeed = message(4)
        // 当前速度
        val currentSpeedTx = dialogTx(5)
        val currentSpeedRx = dialogRx(5)
        // 编码器清零
        val encoderReset = signal(6)
        // 设置超时时间
        val timeout = message(7)
    }

    operator fun component1() = type
    operator fun component2() = index

    protected fun dialogTx(msgType: Int) = signal(msgType)
    protected fun dialogRx(msgType: Int) = message(msgType)

    protected fun signal(msgType: Int) =
        AutoCANPackageHead.WithoutData(
                network = 0,
                priority = 0,
                nodeType = type,
                nodeIndex = index,
                messageType = msgType.toByte())

    protected fun message(msgType: Int) =
        AutoCANPackageHead.WithData(
                network = 0,
                priority = 0,
                nodeType = type,
                nodeIndex = index,
                messageType = msgType.toByte())
}
