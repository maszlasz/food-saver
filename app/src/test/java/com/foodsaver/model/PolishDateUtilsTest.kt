package com.foodsaver.model

import com.foodsaver.util.PolishDateUtils
import com.foodsaver.util.fuzzyMatch
import com.foodsaver.util.levenshtein
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PolishDateUtilsTest {
    @Test
    fun `levenshtein identical strings returns 0`() {
        assertEquals(0, levenshtein("abc", "abc"))
    }

    @Test
    fun `levenshtein empty vs non-empty`() {
        assertEquals(5, levenshtein("", "hello"))
        assertEquals(5, levenshtein("hello", ""))
    }

    @Test
    fun `levenshtein single edit`() {
        assertEquals(1, levenshtein("kot", "kos"))
        assertEquals(1, levenshtein("dom", "do"))
    }

    @Test
    fun `levenshtein Polish diacritics`() {
        assertEquals(1, levenshtein("grudzień", "grudzien"))
    }

    @Test
    fun `formatExpiration produces correct string`() {
        assertEquals(
            "17 września 2026",
            PolishDateUtils.formatExpiration(LocalDate.of(2026, 9, 17)),
        )
        assertEquals(
            "1 stycznia 2026",
            PolishDateUtils.formatExpiration(LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun `resolveMonth exact genitive`() {
        assertEquals(1, PolishDateUtils.resolveMonth("stycznia"))
        assertEquals(12, PolishDateUtils.resolveMonth("grudnia"))
    }

    @Test
    fun `resolveMonth exact nominative`() {
        assertEquals(1, PolishDateUtils.resolveMonth("styczeń"))
        assertEquals(5, PolishDateUtils.resolveMonth("maj"))
    }

    @Test
    fun `resolveMonth fuzzy with small typo`() {
        assertEquals(9, PolishDateUtils.resolveMonth("wrzesnia"))
        assertEquals(10, PolishDateUtils.resolveMonth("pazdziernika"))
    }

    @Test
    fun `resolveMonth rejects garbage`() {
        assertNull(PolishDateUtils.resolveMonth("xyz"))
        assertNull(PolishDateUtils.resolveMonth("ab"))
    }

    @Test
    fun `matchDay numeric`() {
        assertEquals(1, PolishDateUtils.matchDay("1"))
        assertEquals(31, PolishDateUtils.matchDay("31"))
    }

    @Test
    fun `matchDay Polish ordinals`() {
        assertEquals(1, PolishDateUtils.matchDay("pierwszy"))
        assertEquals(7, PolishDateUtils.matchDay("siódmy"))
    }

    @Test
    fun `matchDay rejects garbage`() {
        assertNull(PolishDateUtils.matchDay("xyzabc"))
    }

    @Test
    fun `matchDayTens matches`() {
        assertEquals(20, PolishDateUtils.matchDayTens("dwudziesty"))
        assertEquals(30, PolishDateUtils.matchDayTens("trzydziestego"))
    }

    @Test
    fun `matchDayTens rejects non-tens`() {
        assertNull(PolishDateUtils.matchDayTens("pierwszy"))
    }

    @Test
    fun `fuzzyMatch blank query matches everything`() {
        assertTrue(fuzzyMatch("masło", ""))
        assertTrue(fuzzyMatch("masło", "  "))
    }

    @Test
    fun `fuzzyMatch exact substring`() {
        assertTrue(fuzzyMatch("sok jabłkowy", "sok"))
        assertTrue(fuzzyMatch("sok jabłkowy", "jabłkowy"))
    }

    @Test
    fun `fuzzyMatch tolerates small typos`() {
        assertTrue(fuzzyMatch("masło", "maslo"))
    }

    @Test
    fun `fuzzyMatch rejects completely different`() {
        assertFalse(fuzzyMatch("masło", "komputer"))
    }
}
