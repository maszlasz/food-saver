package com.foodsaver.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodsaver.R
import com.foodsaver.model.FoodEntry
import com.foodsaver.ui.theme.AppOrange
import com.foodsaver.ui.theme.AppWhite
import com.foodsaver.util.PolishDateUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodEntryCard(
    entry: FoodEntry,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit = {},
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = onEdit,
                ),
        colors = CardDefaults.cardColors(containerColor = AppOrange),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = AppWhite,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = AppWhite.copy(alpha = 0.4f),
            )
            Text(
                text =
                    stringResource(
                        R.string.expiration_prefix,
                        PolishDateUtils.formatExpiration(entry.expiry),
                    ),
                fontSize = 13.sp,
                color = AppWhite.copy(alpha = 0.9f),
            )
        }
    }
}
