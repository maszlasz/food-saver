package com.foodsaver.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PolishEntryParserTest {
    @Test
    fun `parseSegment standard input`() {
        val entry = requireNotNull(PolishEntryParser.parseSegment(listOf("masło", "siódmy"), 12))
        assertNotNull(entry)
        assertEquals("Masło", entry.name)
        assertEquals(7, entry.expiry.dayOfMonth)
        assertEquals(12, entry.expiry.monthValue)
    }

    @Test
    fun `parseSegment multi-word food name`() {
        val entry = requireNotNull(PolishEntryParser.parseSegment(listOf("sok", "jabłkowy", "piąty"), 5))
        assertNotNull(entry)
        assertEquals("Sok jabłkowy", entry.name)
        assertEquals(5, entry.expiry.dayOfMonth)
        assertEquals(5, entry.expiry.monthValue)
    }

    @Test
    fun `parseSegment two-word ordinal day`() {
        val entry =
            requireNotNull(PolishEntryParser.parseSegment(listOf("chleb", "dwudziesty", "piąty"), 9))
        assertNotNull(entry)
        assertEquals("Chleb", entry.name)
        assertEquals(25, entry.expiry.dayOfMonth)
        assertEquals(9, entry.expiry.monthValue)
    }

    @Test
    fun `parseSegment numeric day`() {
        val entry = requireNotNull(PolishEntryParser.parseSegment(listOf("jogurt", "16"), 8))
        assertNotNull(entry)
        assertEquals("Jogurt", entry.name)
        assertEquals(16, entry.expiry.dayOfMonth)
        assertEquals(8, entry.expiry.monthValue)
    }

    @Test
    fun `parseSegment returns null for empty words`() {
        assertNull(PolishEntryParser.parseSegment(emptyList(), 1))
    }

    @Test
    fun `parseSegment returns null when day is missing`() {
        assertNull(PolishEntryParser.parseSegment(listOf("sok", "ananasowy"), 1))
    }

    @Test
    fun `parseSegment capitalises first letter`() {
        val entry = requireNotNull(PolishEntryParser.parseSegment(listOf("szynka", "3"), 3))
        assertEquals("Szynka", entry.name)
    }
}
