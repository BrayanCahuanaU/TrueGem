package com.example.reconocedordinero

import com.example.reconocedordinero.domain.StabilityFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StabilityFilterTest {

    @Test
    fun estable_con_conteo_repetido() {
        val filtro = StabilityFilter()
        val conteo = mapOf("20-soles" to 1)
        repeat(5) { filtro.push(conteo) }
        assertEquals(conteo, filtro.getStable())
    }

    @Test
    fun no_estable_con_conteos_cambiantes() {
        val filtro = StabilityFilter()
        repeat(8) { i -> filtro.push(mapOf("m" to i)) }
        assertNull(filtro.getStable())
    }

    @Test
    fun no_estable_sin_piezas() {
        val filtro = StabilityFilter()
        repeat(8) { filtro.push(emptyMap()) }
        assertNull(filtro.getStable())
    }

    @Test
    fun se_reinicia_con_reset() {
        val filtro = StabilityFilter()
        repeat(8) { filtro.push(mapOf("m1" to 1)) }
        filtro.reset()
        assertNull(filtro.getStable())
    }

    @Test
    fun gana_el_conteo_mas_repetido() {
        val filtro = StabilityFilter()
        val frecuente = mapOf("m1" to 1)
        val esporadico = mapOf("m2" to 2)
        repeat(6) { filtro.push(frecuente) }
        repeat(2) { filtro.push(esporadico) }
        assertEquals(frecuente, filtro.getStable())
    }
}
