package com.alexsiri7.unreminder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationTagTest {

    @Test
    fun `LocationTag has exactly 4 values`() {
        assertEquals(4, LocationTag.entries.size)
    }

    @Test
    fun `LocationTag values are correct`() {
        val expected = setOf("HOME", "WORK", "COMMUTE", "ANYWHERE")
        val actual = LocationTag.entries.map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
