package cn.autolabor.transform.struct

/**
 * 联通节点类型 [Node] 的路径
 */
interface Path<Node : Any> {
    val destination: Node

    companion object {
        fun <T : Any> to(t: T) = object : Path<T> {
            override val destination = t
        }
    }
}
