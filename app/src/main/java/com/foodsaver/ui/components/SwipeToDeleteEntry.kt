package com.foodsaver.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.foodsaver.model.FoodEntry
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteEntry(
    entry: FoodEntry,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) {
                    onRemove()
                    true
                } else {
                    false
                }
            },
        )

    val density = LocalDensity.current
    val thresholdPx = with(density) { 160.dp.toPx() }
    val offset = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
    val cardAlpha = (1f - abs(offset) / thresholdPx).coerceIn(0f, 1f)

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {},
    ) {
        FoodEntryCard(
            entry = entry,
            modifier = Modifier.alpha(cardAlpha),
            onEdit = onEdit,
        )
    }
}
