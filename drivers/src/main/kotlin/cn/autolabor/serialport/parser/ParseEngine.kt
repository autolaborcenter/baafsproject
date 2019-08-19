package cn.autolabor.serialport.parser

/**
 * 串口链路层解析器
 * @param TWord 链路码字类型
 * @param TResult 帧解析类型
 * @param parser 网络层解析器
 */
class ParseEngine<TWord, TResult>(
    private val parser: (List<TWord>) -> ParseInfo<TResult>
) {
    /**
     * 一次解析结果
     * @param nextHead 下一个包头的位置
     * @param nextBegin 下一次解析的开始位置
     * @param result 此次解析结果
     */
    data class ParseInfo<Result>(
        val nextHead: Int,
        val nextBegin: Int,
        val result: Result
    )

    /**
     * 执行解析
     * @param list 新数据包
     * @param callback 应用层解析器
     */
    operator fun invoke(
        list: Iterable<TWord>,
        callback: (TResult) -> Unit
    ) {
        // 连接到解析缓冲区
        buffer.addAll(list)
        // 初始化迭代器
        val size = buffer.size
        var begin = 0
        var passed = 0
        // 解析到全部已检查
        while (begin < size && passed < size) {
            val (nextHead, nextBegin, result) = parser(buffer.subList(begin, size))
            callback(result)
            passed = begin + nextBegin
            begin += nextHead
        }
        // 拷贝未解析部分
        buffer = arrayListOf<TWord>().apply { addAll(buffer.subList(begin, size)) }
    }

    // 缓冲
    private var buffer = arrayListOf<TWord>()
}
