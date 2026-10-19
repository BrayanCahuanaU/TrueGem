package com.example.reconocedordinero.domain

import java.util.Locale

/**
 * Formato de dinero en español (Perú). Todos los valores son céntimos (`Int`), nunca `Double`.
 */
object MoneyFormatter {

    /** Valor en céntimos de cada etiqueta del modelo (etiquetas fuera del mapa, como `rev`, se ignoran). */
    // TODO v2: piezas de 5 céntimos y billetes de 200 soles (el modelo no tiene esas clases).
    private val VALOR = mapOf(
        "100-soles" to 10000, "50-soles" to 5000, "20-soles" to 2000, "10-soles" to 1000,
        "m5" to 500, "m2" to 200, "m1" to 100,
        "m50" to 50, "m20" to 20, "m10" to 10,
    )

    private val NUMEROS = listOf(
        "cero", "un", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
        "dieciocho", "diecinueve", "veinte",
    )

    /** `true` si la etiqueta corresponde a un billete. */
    fun isBill(label: String): Boolean = label.endsWith("-soles")

    /** Valor en céntimos de una etiqueta, o 0 si no se reconoce. */
    fun valueCents(label: String): Int = VALOR[label] ?: 0

    /** `true` si la etiqueta cuenta para el total (excluye `rev` y desconocidas). */
    fun isMoney(label: String): Boolean = label in VALOR

    /** Texto en pantalla del monto total: `S/ 35,00` (coma decimal, siempre dos decimales). */
    fun formatAmountForDisplay(cents: Int): String =
        String.format(Locale.US, "S/ %d,%02d", cents / 100, kotlin.math.abs(cents % 100))

    /** Valor corto sin decimales cuando es exacto: `S/ 20`, `S/ 1`; con decimales: `S/ 0,50`. */
    fun formatValue(cents: Int): String =
        if (cents % 100 == 0) "S/ ${cents / 100}"
        else String.format(Locale.US, "S/ %d,%02d", cents / 100, cents % 100)

    /** Frase de total hablada. */
    fun speakTotal(cents: Int): String {
        val soles = cents / 100
        val cent = cents % 100
        val parteSoles = when {
            soles == 0 -> null
            soles == 1 -> "1 sol"
            else -> "$soles soles"
        }
        val parteCent = when {
            cent == 0 -> null
            cent == 1 -> "1 céntimo"
            else -> "$cent céntimos"
        }
        val cuerpo = listOfNotNull(parteSoles, parteCent).joinToString(" con ")
        return "Total: ${cuerpo.ifEmpty { "0 céntimos" }}"
    }

    /** Nombre de la fila del resultado: `Billete S/ 20`, `Moneda S/ 5`, `Moneda S/ 0,50`. */
    fun rowName(label: String): String =
        (if (isBill(label)) "Billete " else "Moneda ") + formatValue(valueCents(label))

    /** Etiqueta corta del overlay: `Billete S/ 20` para billetes; `S/ 1`, `S/ 0,50` para monedas. */
    fun overlayLabel(label: String): String =
        if (isBill(label)) "Billete " + formatValue(valueCents(label)) else formatValue(valueCents(label))

    /** Orden de presentación: billetes primero y luego monedas, cada grupo de mayor a menor valor. */
    fun sortedLabels(counts: Map<String, Int>): List<String> =
        counts.keys.filter { isMoney(it) }
            .sortedWith(compareByDescending<String> { isBill(it) }.thenByDescending { valueCents(it) })

    /** Unidad de una pieza: `20 soles`, `5 soles`, `1 sol`, `50 céntimos`. */
    private fun unitPhrase(label: String): String = when (label) {
        "100-soles" -> "100 soles"
        "50-soles" -> "50 soles"
        "20-soles" -> "20 soles"
        "10-soles" -> "10 soles"
        "m5" -> "5 soles"
        "m2" -> "2 soles"
        "m1" -> "1 sol"
        "m50" -> "50 céntimos"
        "m20" -> "20 céntimos"
        "m10" -> "10 céntimos"
        else -> label
    }

    private fun numeroPalabras(n: Int): String =
        if (n in NUMEROS.indices) NUMEROS[n] else n.toString()

    private fun capitalizar(texto: String): String =
        texto.replaceFirstChar { it.uppercaseChar() }

    /**
     * Frase con singular y plural correctos y artículos:
     * `Un billete de 20 soles`, `Una moneda de 5 soles`, `Dos monedas de 1 sol`.
     */
    fun piecePhrase(label: String, qty: Int): String {
        val unidad = unitPhrase(label)
        return if (isBill(label)) {
            if (qty == 1) "Un billete de $unidad"
            else "${capitalizar(numeroPalabras(qty))} billetes de $unidad"
        } else {
            if (qty == 1) "Una moneda de $unidad"
            else "${capitalizar(numeroPalabras(qty))} monedas de $unidad"
        }
    }

    /**
     * Frase de detalle completa, con la primera pieza en mayúscula:
     * `Un billete de 20 soles, una moneda de 5 soles, dos monedas de 1 sol`.
     */
    fun speakDetail(counts: Map<String, Int>): String =
        sortedLabels(counts).mapIndexed { index, label ->
            val pieza = piecePhrase(label, counts.getValue(label))
            if (index == 0) pieza else pieza.replaceFirstChar { it.lowercaseChar() }
        }.joinToString(", ")
}
