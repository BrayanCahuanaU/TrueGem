package com.example.reconocedordinero.domain

object MoneyFormatter {
    // input: cents (Int)
    fun formatAmountForDisplay(cents: Int): String {
        val soles = cents / 100
        val c = cents % 100
        return String.format("S/ %d,%02d", soles, kotlin.math.abs(c))
    }

    fun speakTotal(cents: Int): String {
        if (cents == 0) return "Total: 0 céntimos"
        val soles = cents / 100
        val c = cents % 100
        val parts = mutableListOf<String>()
        if (soles != 0) {
            parts += when (soles) {
                1 -> "1 sol"
                else -> "$soles soles"
            }
        }
        if (c != 0) {
            parts += when (c) {
                1 -> "1 céntimo"
                else -> "$c céntimos"
            }
        }
        return "Total: ${parts.joinToString(" con ")}".replace("Total:  con ", "Total: ")
    }

    fun detailPhrase(counts: Map<String, Int>, labelsToName: (String)->String): String {
        val rows = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>>{valueOrder(it.key)}.thenByDescending{it.key})
            .map { (label, qty) ->
                val name = labelsToName(label)
                val noun = if (isBill(label)) "billete" else "moneda"
                val article = when(qty){1-> if (noun=="billete") "Un" else "Una"; else -> qty.toString()}
                val unit = unitName(label)
                if (qty==1) "$article $noun de $unit" else "$qty ${if (noun=="billete") "billetes" else "monedas"} de $unit"
            }
        return rows.joinToString(", ")
    }

    private fun isBill(label: String) = label.endsWith("-soles")
    private fun unitName(label: String): String {
        return if (isBill(label)) {
            val v = label.removeSuffix("-soles")
            "$v soles"
        } else {
            val mapping = mapOf("m1" to "1 sol", "m2" to "2 soles", "m5" to "5 soles", "m10" to "10 céntimos", "m20" to "20 céntimos", "m50" to "50 céntimos")
            mapping[label] ?: label
        }
    }

    private fun valueOrder(label: String): Int {
        return when(label) {
            "100-soles"->10000
            "50-soles"->5000
            "20-soles"->2000
            "10-soles"->1000
            "m5"->500
            "m2"->200
            "m1"->100
            "m50"->50
            "m20"->20
            "m10"->10
            else->0
        }
    }
}
