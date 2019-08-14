package serialport.parser

/**
 * 串口链路层解析器
 * @param TWord 链路码字类型
 * @param TResult 帧解析类型
 * @param parser 网络层解析器
 */
class ParseEngine<TWord, TResult>(
    private val parser: Parser<TWord, TResult>
) {
    /**
     * 执行解析
     * @param list 新数据包
     * @param callback 应用层解析器
     */
    operator fun invoke(
        list: Iterable<TWord>,
        callback: (TResult) -> Unit) {
        // 连接到解析缓冲区
        buffer.addAll(list)
        // 初始化迭代器
        val size = buffer.size
        var begin = 0
        var passed = 0
        // 解析到全部已检查
        while (begin < size && passed < size) {
            val (nextBegin, thisPassed, result) = parser(buffer.subList(begin, size))
            callback(result)
            passed = begin + thisPassed
            begin += nextBegin
        }
        // 拷贝未解析部分
        val newArray = ArrayList<TWord>(size - passed)
        buffer.subList(begin, size).forEach { newArray.add(it) }
        buffer = newArray
    }

    // 缓冲
    private var buffer = arrayListOf<TWord>()
}
