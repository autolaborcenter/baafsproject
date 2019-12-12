package cn.autolabor.serialport.manager

import cn.autolabor.serialport.manager.OpenCondition.Certain
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.channel
import org.mechdancer.exceptions.DeviceOfflineException
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.ExceptionMessage.Occurred
import org.mechdancer.exceptions.ExceptionMessage.Recovered

/** 串口管理器 */
class SerialPortManager(
    private val exceptions: SendChannel<ExceptionMessage>
) {
    private val waitingListCertain = mutableSetOf<SerialPortDevice>()
    private val waitingListNormal = mutableSetOf<SerialPortDevice>()
    private val devices = mutableMapOf<SerialPort, Job>()

    @Synchronized
    internal fun register(device: SerialPortDevice) {
        if (device.openCondition is Certain)
            waitingListCertain.add(device)
        else
            waitingListNormal.add(device)
    }

    @ObsoleteCoroutinesApi
    @Synchronized
    fun sync(): Collection<String> {
        println("---- sync serial ports ----")
        // 处理确定名字的目标串口
        waitingListCertain
            .removeIf { device ->
                val name = (device.openCondition as Certain).name
                val port = SerialPort.getCommPort(name)
                port.certificate(device)
            }
        if (waitingListNormal.isEmpty())
            return waitingListCertain.map { it.tag }
        // 找到所有串口
        val ports =
            SerialPort.getCommPorts()
                .asSequence()
                .filter { port ->
                    val name = port.systemPortName.toLowerCase()
                    "com" in name || "ttyusb" in name || "ttyacm" in name
                }
                .filter { port ->
                    val name = port.systemPortName
                    devices.keys.none { it.systemPortName == name }
                }
                .toMutableSet()
        // 逐个测试串口
        waitingListNormal
            .removeIf { device ->
                val predicate = (device.openCondition as? OpenCondition.Filter)?.predicate
                val availablePorts = ports.filter { false != predicate?.invoke(it) }
                if (availablePorts.isEmpty()) {
                    println("searching ${device.tag} but no available port")
                    false
                } else
                    null != availablePorts
                        .firstOrNull { it.certificate(device) }
                        ?.also { ports.remove(it) }
            }
        return (waitingListCertain + waitingListNormal).map { it.tag }
    }

    @ObsoleteCoroutinesApi
    private fun SerialPort.certificate(device: SerialPortDevice): Boolean {
        print("searching ${device.tag} on $systemPortName -> $descriptivePortName")
        // 设置串口
        baudRate = device.baudRate
        setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
        // 开串口
        if (!openPort()) {
            println(": failed")
            return false
        }
        // 创建缓冲区
        val buffer = ByteArray(device.bufferSize)
        // 确认
        val certificator = device.buildCertificator()
        if (certificator != null) {
            val activate = certificator.activeBytes
            writeBytes(activate, activate.size.toLong())
            while (true) {
                val actual = readBytes(buffer, buffer.size.toLong())
                if (actual < 0) {
                    closePort()
                    println(": failed")
                    return false
                }
                val result = actual.takeIf { it > 0 }
                                 ?.let(buffer::take)
                                 ?.let(certificator::invoke)
                             ?: continue
                if (result) break
                else {
                    closePort()
                    println(": failed")
                    return false
                }
            }
        }
        // 开协程
        devices[this] =
            launchSingleThreadJob(device.tag) {
                val fromDriver = channel<List<Byte>>()
                val toDriver = channel<List<Byte>>()
                device.setup(CoroutineScope(Dispatchers.IO),
                             toDevice = fromDriver,
                             fromDevice = toDriver)
                launch(Dispatchers.IO) {
                    for (bytes in fromDriver)
                        writeBytes(bytes.toByteArray(), bytes.size.toLong())
                }
                val offlineException = DeviceOfflineException(device.tag)
                while (isActive) {
                    readOrReboot(buffer, 100L)
                    { exceptions.send(Occurred(offlineException)) }
                        .takeUnless(Collection<*>::isEmpty)
                        ?.let {
                            exceptions.send(Recovered(offlineException))
                            toDriver.send(it)
                        }
                }
            }
        println(": done")
        return true
    }

    private companion object {
        @ObsoleteCoroutinesApi
        fun launchSingleThreadJob(tag: String, block: suspend CoroutineScope.() -> Unit) =
            GlobalScope.launch(newSingleThreadContext(tag), block = block)

        /**
         * 从串口读取，并在超时时自动重启串口
         * @param buffer 缓冲区
         * @param retryInterval 重试间隔
         * @param block 异常报告回调
         */
        suspend fun SerialPort.readOrReboot(
            buffer: ByteArray,
            retryInterval: Long,
            block: suspend () -> Unit = {}
        ): List<Byte> {
            val size = buffer.size.toLong()
            // 反复尝试读取
            while (true) {
                // 在单线程上打开串口并阻塞读取
                when (val actual = takeIf { it.isOpen || it.openPort() }?.readBytes(buffer, size)) {
                    null, -1 ->
                        block()
                    0        -> {
                        // 如果长度是 0,的可能是假的,发送空包可更新串口对象状态
                        writeBytes(byteArrayOf(), 0)
                        if (!isOpen) block()
                    }
                    else     ->
                        return buffer.take(actual)
                }
                // 等待一段时间重试
                delay(retryInterval)
            }
        }
    }
}
