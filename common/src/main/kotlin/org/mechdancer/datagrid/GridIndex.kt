package org.mechdancer.datagrid

/** 网格序号 */
interface GridIndex<G : GridIndex<G>> : List<Int> {
    /** 邻域 */
    val neighbors: Set<G>
}
