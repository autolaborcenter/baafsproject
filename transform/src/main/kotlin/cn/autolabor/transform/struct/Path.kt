//package cn.autolabor.transform.struct
//
///**
// * 联通节点类型 [Node] 的路径
// */
//interface Path<Node : Any> {
//    val destination: Node
//
//    companion object {
//        fun <T : Any> to(t: T) = object : Path<T> {
//            override val destination = t
//        }
//    }
//}
//
///** 安全获取链表元素 */
//fun <TNode : Any, TPath : Path<TNode>>
//    Map<TNode, Iterable<TPath>>.getOrEmpty(node: TNode) =
//    getOrDefault(node, emptySet())
//
///** 获取链表元素的字符串表示 */
//fun <TNode : Any, TPath : Path<TNode>>
//    Map<TNode, Iterable<TPath>>.view(node: TNode) =
//    "$node: ${getOrEmpty(node)}"
//
///**
// * 从[root]开始（反）拓扑排序
// * @return 连通的、可顺序构造的节点列表
// */
//fun <TNode : Any, TPath : Path<TNode>>
//    Map<TNode, Iterable<TPath>>.sort(root: TNode) =
//    mutableListOf(root)
//        .also { sub ->
//            // 剩余项
//            val rest = keys.toMutableSet().apply { remove(root) }
//            // 已接纳指针
//            var ptr = 0
//            // 若仍有未连通的
//            while (rest.isNotEmpty())
//            // 已连通的全部检查过，直接返回
//            // 否则尝试从邻接表中获取
//                get(sub.getOrNull(ptr++) ?: break)
//                    // 找到所有连接到的节点
//                    ?.map(Path<TNode>::destination)
//                    ?.toMutableSet()
//                    // 取其中非叶子但未连接的
//                    ?.apply { retainAll(rest) }
//                    // 从未连接的移除，添加到已连接的
//                    ?.also {
//                        rest.removeAll(it)
//                        sub.addAll(it)
//                    }
//        }
//
///** 构造包含[root]的连通子图 */
//fun <TNode : Any, TPath : Path<TNode>>
//    Map<TNode, Iterable<TPath>>.subWith(root: TNode) =
//    sort(root)
//        .associateWith {
//            val list = getOrEmpty(it)
//            (list as? Set) ?: list.toSet()
//        }
