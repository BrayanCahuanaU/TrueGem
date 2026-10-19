package com.example.reconocedordinero

import com.example.reconocedordinero.domain.StabilityFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StabilityFilterTest {
    @Test
    fun testStable() {
        val f = StabilityFilter()
        val c1 = mapOf("20-soles" to 1)
        repeat(5) { f.push(c1) }
        assertEquals(c1, f.getStable())
    }

    @Test
    fun testNotStableChanging() {
        val f = StabilityFilter()
        f.push(mapOf("20-soles" to 1))
        f.push(mapOf("10-soles" to 1))
        f.push(mapOf("20-soles" to 1))
        assertNull(f.getStable())
    }

    @Test
    fun testNotStableEmpty() {
        val f = StabilityFilter()
        assertNull(f.getStable())
    }
}
