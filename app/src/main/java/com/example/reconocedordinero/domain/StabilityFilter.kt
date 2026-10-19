package com.example.reconocedordinero.domain

/**
 * Filtro de estabilidad: guarda el conteo por etiqueta (ignorando `rev`) de los últimos
 * [window] frames. Es estable cuando el mismo conteo, con al menos una pieza, aparece en
 * [needed] o más de esos frames. Devuelve el conteo ganador. Se reinicia al empezar un escaneo.
 */
class StabilityFilter(private val window: Int = 8, private val needed: Int = 5) {

    private val history = ArrayDeque<Map<String, Int>>()

    fun push(count: Map<String, Int>) {
        if (history.size >= window) history.removeFirst()
        history.addLast(count.toMap())
    }

    /** Conteo estable, o `null` si todavía no hay uno. */
    fun getStable(): Map<String, Int>? {
        if (history.isEmpty()) return null
        val (ganador, veces) = history.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        if (veces < needed) return null
        if (ganador.values.sum() == 0) return null   // al menos una pieza
        return ganador
    }

    fun reset() = history.clear()
}
