package org.mechdancer.datagrid

val dimDelta = listOf(0, -1, +1)

fun <G : GridIndex<G>> G.times3() =
    sequence { var i = 1; repeat(size) { yield(i); i *= 3 } }.toList().asReversed()

fun <G : GridIndex<G>> G.rowView() =
    joinToString(" ", "(", ")")

fun <G : GridIndex<G>> G.elementEquals(others: G) =
    this.zip(others) { a, b -> a == b }.all { it }

fun <G : GridIndex<G>> G.listHash() =
    reduce { code, it -> code.hashCode() * 31 + it.hashCode() }
