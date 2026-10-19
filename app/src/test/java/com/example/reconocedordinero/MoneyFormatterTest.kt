package com.example.reconocedordinero

import com.example.reconocedordinero.domain.MoneyFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun testFormatDisplay() {
        assertEquals("S/ 0,30", MoneyFormatter.formatAmountForDisplay(30))
        assertEquals("S/ 1,00", MoneyFormatter.formatAmountForDisplay(100))
        assertEquals("S/ 1,10", MoneyFormatter.formatAmountForDisplay(110))
        assertEquals("S/ 35,00", MoneyFormatter.formatAmountForDisplay(3500))
        assertEquals("S/ 35,50", MoneyFormatter.formatAmountForDisplay(3550))
    }

    @Test
    fun testSpeakTotal() {
        assertEquals("Total: 35 soles", MoneyFormatter.speakTotal(3500))
        assertEquals("Total: 1 sol", MoneyFormatter.speakTotal(100))
        assertEquals("Total: 30 céntimos", MoneyFormatter.speakTotal(30))
        assertEquals("Total: 35 soles con 50 céntimos", MoneyFormatter.speakTotal(3550))
    }
}
