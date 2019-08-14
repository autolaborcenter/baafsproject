package serialport.parser

/**
 * 解析器类型
 * @param TWord 字类型
 * @param TResult 返回值类型
 */
interface Parser<TWord, TResult> {
    /**
     * 一次解析结果
     * @param nextBegin 下一次解析的起始位置
     * @param passed 已检视的字数量
     * @param result 此次解析结果
     */
    data class ParseInfo<Result>(val nextBegin: Int, val passed: Int, val result: Result)

    /**
     * 进行一次解析
     * @param buffer 总缓冲区
     * @return 一次解析结果
     */
    operator fun invoke(buffer: List<TWord>): ParseInfo<TResult>
}
