package com.yunfie.illustia

import com.yunfie.illustia.models.Illust

fun interleaveIllusts(
    first: List<Illust>,
    second: List<Illust>,
): List<Illust> =
    buildList(first.size + second.size) {
        val seenIds = HashSet<Long>(first.size + second.size)
        repeat(maxOf(first.size, second.size)) { index ->
            first.getOrNull(index)?.takeIf { seenIds.add(it.id) }?.let(::add)
            second.getOrNull(index)?.takeIf { seenIds.add(it.id) }?.let(::add)
        }
    }
