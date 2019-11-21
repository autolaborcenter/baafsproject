package cn.autolabor.autocan

import cn.autolabor.serialport.parser.serial4Bytes.buildSerial4Bytes
import java.io.ByteArrayOutputStream

/** Auto CAN 包头结构 */
internal sealed class AutoCANPackageHead {
    abstract val network: Byte
    abstract val priority: Byte
    abstract val nodeType: Byte
    abstract val nodeIndex: Byte
    abstract val messageType: Byte

    /** 有数据段的数据包 */
    data class WithData(
        override val network: Byte,
        override val priority: Byte,
        override val nodeType: Byte,
        override val nodeIndex: Byte,
        override val messageType: Byte
    ) : AutoCANPackageHead() {
        private val stub = buildStub(true)
        fun pack(frameId: Byte = 0, data: ByteArray = byteArrayOf()): ByteArray {
            require(data.size <= 8)
            return ByteArrayOutputStream(14)
                .apply {
                    writeBytes(stub)
                    write(frameId.toInt())
                    writeBytes(data)
                    for (i in 1..8 - data.size) write(0)
                }
                .fillCrc8()
        }
    }

    /** 无数据段的数据包 */
    data class WithoutData(
        override val network: Byte,
        override val priority: Byte,
        override val nodeType: Byte,
        override val nodeIndex: Byte,
        override val messageType: Byte
    ) : AutoCANPackageHead() {
        private val stub = buildStub(false)
        private val zeroStub by lazy { build(0) }

        fun pack() = zeroStub
        fun pack(reserve: Byte) = if (reserve == 0.toByte()) zeroStub else build(reserve)

        private fun build(reserve: Byte): ByteArray =
            ByteArrayOutputStream(6)
                .apply {
                    writeBytes(stub)
                    write(reserve.toInt())
                }
                .fillCrc8()
    }

    protected fun buildStub(dataField: Boolean) =
        buildSerial4Bytes {
            write(8, 0xfe)
            write(2, network.toInt())
            write(1, if (dataField) 1 else 0)
            write(3, priority.toInt())
            write(6, nodeType.toInt())
            write(4, nodeIndex.toInt())
            write(8, messageType.toInt())
        }

    companion object {
        protected fun ByteArrayOutputStream.fillCrc8() =
            apply { write(0) }
                .toByteArray()
                .apply { this[lastIndex] = copyOfRange(1, lastIndex).calculateCrc8().toByte() }

        private fun ByteArray.calculateCrc8() =
            fold(0) { sum, it -> crc8[(sum xor it.toInt()).asUnsignedByte()] }

        private fun Int.asUnsignedByte() =
            if (this < 0) this + 256 else this

        private val crc8 = intArrayOf(
                0, 94, 188, 226, 97, 63, 221, 131, 194, 156, 126, 32, 163, 253, 31, 65,
                157, 195, 33, 127, 252, 162, 64, 30, 95, 1, 227, 189, 62, 96, 130, 220,
                35, 125, 159, 193, 66, 28, 254, 160, 225, 191, 93, 3, 128, 222, 60, 98,
                190, 224, 2, 92, 223, 129, 99, 61, 124, 34, 192, 158, 29, 67, 161, 255,
                70, 24, 250, 164, 39, 121, 155, 197, 132, 218, 56, 102, 229, 187, 89, 7,
                219, 133, 103, 57, 186, 228, 6, 88, 25, 71, 165, 251, 120, 38, 196, 154,
                101, 59, 217, 135, 4, 90, 184, 230, 167, 249, 27, 69, 198, 152, 122, 36,
                248, 166, 68, 26, 153, 199, 37, 123, 58, 100, 134, 216, 91, 5, 231, 185,
                140, 210, 48, 110, 237, 179, 81, 15, 78, 16, 242, 172, 47, 113, 147, 205,
                17, 79, 173, 243, 112, 46, 204, 146, 211, 141, 111, 49, 178, 236, 14, 80,
                175, 241, 19, 77, 206, 144, 114, 44, 109, 51, 209, 143, 12, 82, 176, 238,
                50, 108, 142, 208, 83, 13, 239, 177, 240, 174, 76, 18, 145, 207, 45, 115,
                202, 148, 118, 40, 171, 245, 23, 73, 8, 86, 180, 234, 105, 55, 213, 139,
                87, 9, 235, 181, 54, 104, 138, 212, 149, 203, 41, 119, 244, 170, 72, 22,
                233, 183, 85, 11, 136, 214, 52, 106, 43, 117, 151, 201, 74, 20, 246, 168,
                116, 42, 200, 150, 21, 75, 169, 247, 182, 232, 10, 84, 215, 137, 107, 53)
    }
}