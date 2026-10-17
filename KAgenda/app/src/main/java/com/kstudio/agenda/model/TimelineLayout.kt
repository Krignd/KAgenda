package com.kstudio.agenda.model

/** 同一时间冲突的条目并排，首尾相接的条目不占用同一冲突组。 */
object TimelineLayout {
    data class Item<T>(val value: T, val start: Int, val end: Int)
    data class Placed<T>(val item: Item<T>, val column: Int, val columns: Int)
    fun <T> place(items: List<Item<T>>): List<Placed<T>> {
        val sorted = items.filter { it.end > it.start }.sortedWith(compareBy({ it.start }, { it.end }))
        val result = mutableListOf<Placed<T>>()
        var group = mutableListOf<Item<T>>()
        var groupEnd = Int.MIN_VALUE
        fun flush() {
            val ends = mutableListOf<Int>()
            val lanes = group.map { item ->
                var lane = ends.indexOfFirst { it <= item.start }
                if (lane < 0) { lane = ends.size; ends.add(item.end) } else ends[lane] = item.end
                item to lane
            }
            lanes.forEach { (item, lane) -> result += Placed(item, lane, ends.size) }
            group = mutableListOf()
        }
        sorted.forEach { item ->
            if (group.isNotEmpty() && item.start >= groupEnd) { flush(); groupEnd = Int.MIN_VALUE }
            group += item; groupEnd = maxOf(groupEnd, item.end)
        }
        if (group.isNotEmpty()) flush()
        return result
    }
}
