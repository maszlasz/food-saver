package com.foodsaver.util

import kotlin.math.min

fun levenshtein(
    a: String,
    b: String,
): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) {
        dp[i][0] = i
    }
    for (j in 0..b.length) {
        dp[0][j] = j
    }
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            dp[i][j] =
                if (a[i - 1] == b[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
        }
    }
    return dp[a.length][b.length]
}

// Only for searching
fun fuzzyMatch(
    name: String,
    query: String,
): Boolean {
    if (query.isBlank()) {
        return true
    }

//    all query words should match some name word, give more leeway to longer words
    val nameWords = name.lowercase().split(Regex("\\s+"))
    val queryWords = query.lowercase().split(Regex("\\s+"))

    return queryWords.all { queryWord ->
        nameWords.any { nameWord ->
            nameWord.startsWith(queryWord) ||
                levenshtein(queryWord, nameWord) <= min(1, queryWord.length / 3)
        }
    }
}
