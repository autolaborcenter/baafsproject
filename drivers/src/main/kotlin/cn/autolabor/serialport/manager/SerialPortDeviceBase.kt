package cn.autolabor.serialport.manager

abstract class SerialPortDeviceBase(
    override val tag: String,
    override val baudRate: Int,
    override val bufferSize: Int,

    portName: String?
) : SerialPortDevice {
    override val openCondition =
        portName?.let(OpenCondition::Certain)
            ?: OpenCondition.None

    protected abstract class CertificatorBase(
        private val timeout: Long
    ) : Certificator {
        private val t0 = System.currentTimeMillis()
        private val duration get() = System.currentTimeMillis() - t0
        protected fun passOrTimeout(predicate: Boolean) =
            when {
                predicate          -> true
                duration > timeout -> false
                else               -> null
            }
    }
}
