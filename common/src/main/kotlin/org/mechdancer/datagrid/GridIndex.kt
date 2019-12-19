package org.mechdancer.datagrid

/** 网格序号 */
interface GridIndex<I : GridIndex<I>> : List<Int> {
    /** 邻域 */
    val neighbors: Set<I>
}
