package com.foodsaver.model

import java.time.LocalDate
import java.util.UUID

data class FoodEntry(
    val name: String,
    val expiry: LocalDate,
    val id: String = UUID.randomUUID().toString(),
)

enum class SortMode {
    ALPHA,
    DATE,
}
