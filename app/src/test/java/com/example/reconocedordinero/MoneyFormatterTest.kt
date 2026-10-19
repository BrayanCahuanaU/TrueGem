package com.example.reconocedordinero

import com.example.reconocedordinero.domain.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {

    @Test
    fun formato_en_pantalla() {
        assertEquals("S/ 0,30", MoneyFormatter.formatAmountForDisplay(30))
        assertEquals("S/ 1,00", MoneyFormatter.formatAmountForDisplay(100))
        assertEquals("S/ 1,10", MoneyFormatter.formatAmountForDisplay(110))
        assertEquals("S/ 35,00", MoneyFormatter.formatAmountForDisplay(3500))
        assertEquals("S/ 35,50", MoneyFormatter.formatAmountForDisplay(3550))
    }

    @Test
    fun total_hablado() {
        assertEquals("Total: 30 céntimos", MoneyFormatter.speakTotal(30))
        assertEquals("Total: 1 sol", MoneyFormatter.speakTotal(100))
        assertEquals("Total: 1 sol con 10 céntimos", MoneyFormatter.speakTotal(110))
        assertEquals("Total: 35 soles", MoneyFormatter.speakTotal(3500))
        assertEquals("Total: 35 soles con 50 céntimos", MoneyFormatter.speakTotal(3550))
    }

    @Test
    fun singular_y_plural() {
        assertEquals("Un billete de 20 soles", MoneyFormatter.piecePhrase("20-soles", 1))
        assertEquals("Una moneda de 5 soles", MoneyFormatter.piecePhrase("m5", 1))
        assertEquals("Dos monedas de 1 sol", MoneyFormatter.piecePhrase("m1", 2))
        assertEquals("Una moneda de 50 céntimos", MoneyFormatter.piecePhrase("m50", 1))
        assertEquals("Tres monedas de 10 céntimos", MoneyFormatter.piecePhrase("m10", 3))
    }

    @Test
    fun nombres_de_fila_y_overlay() {
        assertEquals("Billete S/ 20", MoneyFormatter.rowName("20-soles"))
        assertEquals("Moneda S/ 5", MoneyFormatter.rowName("m5"))
        assertEquals("Moneda S/ 0,50", MoneyFormatter.rowName("m50"))
        assertEquals("Billete S/ 10", MoneyFormatter.overlayLabel("10-soles"))
        assertEquals("S/ 1", MoneyFormatter.overlayLabel("m1"))
        assertEquals("S/ 0,50", MoneyFormatter.overlayLabel("m50"))
    }

    @Test
    fun orden_y_detalle() {
        val counts = mapOf("20-soles" to 1, "m5" to 1, "m1" to 2, "m50" to 1, "m10" to 3)

        assertEquals(
            listOf("20-soles", "m5", "m1", "m50", "m10"),
            MoneyFormatter.sortedLabels(counts),
        )
        assertEquals(
            "Un billete de 20 soles, una moneda de 5 soles, dos monedas de 1 sol, " +
                "una moneda de 50 céntimos, tres monedas de 10 céntimos",
            MoneyFormatter.speakDetail(counts),
        )
    }

    @Test
    fun ignora_etiquetas_desconocidas() {
        assertEquals(emptyList<String>(), MoneyFormatter.sortedLabels(mapOf("rev" to 3)))
        assertEquals("", MoneyFormatter.speakDetail(mapOf("rev" to 3)))
    }

    @Test
    fun valores_de_cada_denominacion() {
        assertEquals(1000, MoneyFormatter.valueCents("10-soles"))
        assertEquals(10000, MoneyFormatter.valueCents("100-soles"))
        assertEquals(500, MoneyFormatter.valueCents("m5"))
        assertEquals(10, MoneyFormatter.valueCents("m10"))
        assertEquals(0, MoneyFormatter.valueCents("rev"))
    }
}
