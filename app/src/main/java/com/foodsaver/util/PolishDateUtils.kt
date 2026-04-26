package com.foodsaver.util

import java.time.LocalDate

object PolishDateUtils {
    val monthsGenitive =
        listOf(
            "stycznia",
            "lutego",
            "marca",
            "kwietnia",
            "maja",
            "czerwca",
            "lipca",
            "sierpnia",
            "września",
            "października",
            "listopada",
            "grudnia",
        )

    private val monthForms =
        mapOf(
            "styczeń" to 1,
            "stycznia" to 1,
            "luty" to 2,
            "lutego" to 2,
            "marzec" to 3,
            "marca" to 3,
            "kwiecień" to 4,
            "kwietnia" to 4,
            "maj" to 5,
            "maja" to 5,
            "czerwiec" to 6,
            "czerwca" to 6,
            "lipiec" to 7,
            "lipca" to 7,
            "sierpień" to 8,
            "sierpnia" to 8,
            "wrzesień" to 9,
            "września" to 9,
            "październik" to 10,
            "października" to 10,
            "listopad" to 11,
            "listopada" to 11,
            "grudzień" to 12,
            "grudnia" to 12,
        )

    private val ordinalsSingle: Map<String, Int> =
        mapOf(
            "pierwszy" to 1,
            "pierwszego" to 1,
            "drugi" to 2,
            "drugiego" to 2,
            "trzeci" to 3,
            "trzeciego" to 3,
            "czwarty" to 4,
            "czwartego" to 4,
            "piąty" to 5,
            "piątego" to 5,
            "szósty" to 6,
            "szóstego" to 6,
            "siódmy" to 7,
            "siódmego" to 7,
            "ósmy" to 8,
            "ósmego" to 8,
            "dziewiąty" to 9,
            "dziewiątego" to 9,
            "dziesiąty" to 10,
            "dziesiątego" to 10,
            "jedenasty" to 11,
            "jedenastego" to 11,
            "dwunasty" to 12,
            "dwunastego" to 12,
            "trzynasty" to 13,
            "trzynastego" to 13,
            "czternasty" to 14,
            "czternastego" to 14,
            "piętnasty" to 15,
            "piętnastego" to 15,
            "szesnasty" to 16,
            "szesnastego" to 16,
            "siedemnasty" to 17,
            "siedemnastego" to 17,
            "osiemnasty" to 18,
            "osiemnastego" to 18,
            "dziewiętnasty" to 19,
            "dziewiętnastego" to 19,
            "dwudziesty" to 20,
            "dwudziestego" to 20,
            "trzydziesty" to 30,
            "trzydziestego" to 30,
        ) + (1..31).associateBy { it.toString() }

    private val ordinalsTens =
        mapOf(
            "dwudziesty" to 20,
            "dwudziestego" to 20,
            "trzydziesty" to 30,
            "trzydziestego" to 30,
        )

    fun formatExpiration(expiry: LocalDate): String = "${expiry.dayOfMonth} ${monthsGenitive[expiry.monthValue - 1]} ${expiry.year}"

    fun resolveMonth(word: String): Int? {
        if (word.length < 3) {
            return null
        }

        val (key, distance) =
            monthForms.keys
                .map { it to levenshtein(word, it) }
                .minBy { it.second }

        return if (distance <= 3 && distance * 2 < word.length) monthForms[key] else null
    }

    fun matchDay(word: String): Int? {
        word.toIntOrNull()?.let {
            if (it in 1..31) {
                return it
            }
        }

        val (key, distance) =
            ordinalsSingle.keys
                .map { it to levenshtein(word, it) }
                .minBy { it.second }

        return if (distance <= 3 && distance * 2 < key.length) ordinalsSingle[key] else null
    }

    fun matchDayTens(word: String): Int? {
        val (entry, distance) =
            ordinalsTens.entries
                .map { it to levenshtein(word, it.key) }
                .minBy { it.second }

        return if (distance <= 3) entry.value else null
    }

// Shouldn't be necessary anymore, the current model looks to be always
// splitting up words nicely by itself
//    fun trySplitMerged(word: String): List<String> {
//        if (word.length < 5) {
//            return listOf(word)
//        }
//        if (resolveMonth(word) != null) {
//            return listOf(word)
//        }
//
//        for (index in 2..word.length - 3) {
//            val suffix = word.substring(index)
//            val month = resolveMonth(suffix)
//            if (month != null) {
//                val prefix = word.take(index)
//                return listOf(prefix, suffix)
//            }
//        }
//
//        return listOf(word)
//    }
}
