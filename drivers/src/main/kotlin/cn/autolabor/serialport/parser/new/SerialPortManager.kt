package cn.autolabor.serialport.parser.new

import cn.autolabor.serialport.parser.new.OpenCondition.Certain
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class SerialPortManager {
    private val waitingListCertain = mutableSetOf<BySerialPort>()
    private val waitingListNormal = mutableSetOf<BySerialPort>()
    private val devices = mutableMapOf<SerialPort, Job>()

    @Synchronized
    internal fun register(device: BySerialPort) {
        if (device.openCondition is Certain)
            waitingListCertain.add(device)
        else
            waitingListNormal.add(device)
    }

    @Synchronized
    fun sync() {
        // 处理确定名字的目标串口
        waitingListCertain
            .removeIf { device ->
                SerialPort
                    .getCommPort((device.openCondition as Certain).name)
                    .certificate(device)
            }
        if (waitingListNormal.isEmpty()) return
        // 找到所有串口
        val ports =
            SerialPort.getCommPorts()
                .asSequence()
                .filter { port ->
                    val name = port.systemPortName.toLowerCase()
                    "com" in name || "/dev/usb" in name || "/dev/acm" in name
                }
                .filter { port ->
                    val name = port.systemPortName
                    devices.keys.none { it.systemPortName == name }
                }
                .toMutableSet()
        // 逐个处理串口
        waitingListNormal
            .removeIf { device ->
                null != ports
                    .firstOrNull { it.certificate(device) }
                    ?.also { ports.remove(it) }
            }
    }

    private fun SerialPort.certificate(device: BySerialPort): Boolean {
        // 设置串口
        baudRate = device.baudRate
        setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
        // 开串口
        if (!openPort()) return false
        // 创建缓冲区
        val buffer = ByteArray(device.bufferSize)
        // 确认
        val certificator = device.buildCertificator()
        if (certificator != null)
            while (true) {
                val result =
                    buffer
                        .take(readBytes(buffer, buffer.size.toLong()))
                        .takeUnless(Collection<*>::isEmpty)
                        ?.let(certificator::invoke)
                    ?: continue
                if (result) break
                else return false
            }
        // 开协程
        devices[this] =
            launchSingleThreadJob {
                while (true)
                    buffer
                        .take(readBytes(buffer, buffer.size.toLong()))
                        .takeUnless(Collection<*>::isEmpty)
                        ?.let(device::read)
            }
        return true
    }

    private companion object {
        private fun launchSingleThreadJob(block: suspend CoroutineScope.() -> Unit) =
            GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), block = block)
    }
}
