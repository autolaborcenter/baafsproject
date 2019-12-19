package org.mechdancer.datagrid

val dimDelta = listOf(0, -1, +1)

fun <I : GridIndex<I>> I.times3() =
    sequence { var i = 1; repeat(size) { yield(i); i *= 3 } }.toList().asReversed()

fun <I : GridIndex<I>> I.rowView() =
    joinToString(" ", "(", ")")

fun <I : GridIndex<I>> I.elementEquals(others: I) =
    this.zip(others) { a, b -> a == b }.all { it }

fun <I : GridIndex<I>> I.listHash() =
    reduce { code, it -> code.hashCode() * 31 + it.hashCode() }
