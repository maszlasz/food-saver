package com.foodsaver.speech

import com.foodsaver.model.FoodEntry
import com.foodsaver.util.PolishDateUtils
import java.time.LocalDate
import java.time.LocalDate.of
import java.time.YearMonth

object PolishEntryParser {
//    Try single and double digits, but only if there are enough words for that
//    (assume at least one word from the beginning for food name)
    fun parseSegment(
        words: List<String>,
        month: Int,
    ): FoodEntry? {
        if (words.isEmpty()) {
            return null
        }

        val singleDay = PolishDateUtils.matchDay(words.last())
        val wordsBeforeSingle = words.dropLast(1)
        val tensDay =
            if (wordsBeforeSingle.size >= 2) {
                PolishDateUtils.matchDayTens(wordsBeforeSingle.last())
            } else {
                null
            }

        val (day, nameWords) =
            if (tensDay != null && singleDay != null && singleDay in 1..9) {
                (tensDay + singleDay) to wordsBeforeSingle.dropLast(1)
            } else if (singleDay != null) {
                singleDay to wordsBeforeSingle
            } else {
                return null
            }

        if (nameWords.isEmpty()) {
            return null
        }

        val today = LocalDate.now()
        val year = today.year
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        val clampedDay = day.coerceIn(1, maxDay)

        var expiry = of(year, month, clampedDay)
        if (expiry.isBefore(today)) {
            expiry = expiry.plusYears(1)
        }

        return FoodEntry(cleanName(nameWords.joinToString(" ")), expiry)
    }

    private fun cleanName(raw: String): String {
        val trimmed = raw.trim()
        return trimmed.replaceFirstChar { it.uppercaseChar() }
    }
}
