package cn.autolabor.serialport.manager

import cn.autolabor.serialport.manager.OpenCondition.Certain
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import org.mechdancer.channel
import org.mechdancer.exceptions.ExceptionMessage
import org.mechdancer.exceptions.device.DeviceOfflineException
import java.util.concurrent.Executors

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

    @Synchronized
    fun sync(): Boolean {
        // 处理确定名字的目标串口
        waitingListCertain
            .removeIf { device ->
                val name = (device.openCondition as Certain).name
                val port = SerialPort.getCommPort(name)
                port.certificate(device)
            }
        if (waitingListNormal.isEmpty()) return waitingListCertain.isEmpty()
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
                null != ports
                    .asSequence()
                    .filter { false != predicate?.invoke(it) }
                    .onEach { }
                    .firstOrNull { it.certificate(device) }
                    ?.also { ports.remove(it) }
            }
        return waitingListCertain.isEmpty() && waitingListNormal.isEmpty()
    }

    private fun SerialPort.certificate(device: SerialPortDevice): Boolean {
        println("searching ${device.tag} on $systemPortName -> $descriptivePortName")
        // 设置串口
        baudRate = device.baudRate
        setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 100)
        // 开串口
        if (!openPort()) return false
        // 创建缓冲区
        val buffer = ByteArray(device.bufferSize)
        // 确认
        val certificator = device.buildCertificator()
        if (certificator != null) {
            val activate = certificator.activeBytes
            writeBytes(activate, activate.size.toLong())
            while (true) {
                val result =
                    buffer
                        .take(readBytes(buffer, buffer.size.toLong()))
                        .let(certificator::invoke)
                    ?: continue
                if (result) break
                else {
                    closePort()
                    return false
                }
            }
        }
        // 开协程
        devices[this] =
            launchSingleThreadJob {
                val fromDriver = channel<List<Byte>>()
                val toDriver = channel<List<Byte>>()
                device.setup(CoroutineScope(Dispatchers.IO),
                             toDevice = fromDriver,
                             fromDevice = toDriver)
                launch(Dispatchers.IO) {
                    for (bytes in fromDriver)
                        writeBytes(bytes.toByteArray(), bytes.size.toLong())
                }
                val offline = DeviceOfflineException(device.tag).occurred()
                val online = DeviceOfflineException(device.tag).occurred()
                while (isActive)
                    readOrReboot(buffer, device.retryInterval)
                    { exceptions.send(offline) }
                        .takeUnless(Collection<*>::isEmpty)
                        ?.let {
                            exceptions.send(online)
                            toDriver.send(it)
                        }
            }
        return true
    }

    private companion object {
        private fun launchSingleThreadJob(block: suspend CoroutineScope.() -> Unit) =
            GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher(), block = block)
    }
}
