package com.example.reconocedordinero.domain

class StabilityFilter(private val window: Int = 8, private val needed: Int = 5) {
    private val history = ArrayDeque<Map<String, Int>>()

    fun push(count: Map<String, Int>) {
        if (history.size >= window) history.removeFirst()
        history.addLast(count)
    }

    fun getStable(): Map<String, Int>? {
        if (history.isEmpty()) return null
        val grouped = history.groupingBy { it }.eachCount()
        val (m, c) = grouped.maxByOrNull { it.value } ?: return null
        if (c >= needed && m.values.sum() > 0) return m
        return null
    }

    fun reset() { history.clear() }
}
