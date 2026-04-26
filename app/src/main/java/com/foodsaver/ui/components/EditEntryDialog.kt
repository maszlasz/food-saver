package com.foodsaver.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodsaver.R
import com.foodsaver.model.FoodEntry
import com.foodsaver.ui.theme.AppCardDark
import com.foodsaver.ui.theme.AppGrayText
import com.foodsaver.ui.theme.AppPurple
import com.foodsaver.ui.theme.AppWhite
import com.foodsaver.util.PolishDateUtils
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun EditEntryDialog(
    entry: FoodEntry,
    onDismiss: (FoodEntry) -> Unit,
) {
    val months = PolishDateUtils.monthsGenitive
    val currentYear = LocalDate.now().year
    val years = (currentYear..currentYear + 5).map { it.toString() }

    var editName by remember { mutableStateOf(entry.name) }
    var selectedDay by remember { mutableStateOf(entry.expiry.dayOfMonth) }
    var selectedMonthIndex by remember { mutableStateOf(entry.expiry.monthValue - 1) }
    var selectedYearIndex by
        remember {
            mutableStateOf((entry.expiry.year - currentYear).coerceIn(0, 5))
        }

    val maxDay =
        YearMonth.of(currentYear + selectedYearIndex, selectedMonthIndex + 1).lengthOfMonth()
    val clampedDay = selectedDay.coerceIn(1, maxDay)

    fun buildResult(): FoodEntry =
        FoodEntry(
            editName.trim(),
            LocalDate.of(currentYear + selectedYearIndex, selectedMonthIndex + 1, clampedDay),
            entry.id,
        )

    Dialog(
        onDismissRequest = { onDismiss(buildResult()) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onDismiss(buildResult()) },
            )

            Card(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 28.dp)
                        .clickable(enabled = false) {},
                colors = CardDefaults.cardColors(containerColor = AppCardDark),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.edit_entry),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AppWhite,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.food_name), color = AppGrayText) },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppPurple,
                                unfocusedBorderColor = AppWhite.copy(alpha = 0.5f),
                                focusedTextColor = AppWhite,
                                unfocusedTextColor = AppWhite,
                                cursorColor = AppPurple,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = stringResource(R.string.expiration_date),
                        fontSize = 13.sp,
                        color = AppGrayText,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.day), fontSize = 11.sp, color = AppGrayText)
                            WheelPicker(
                                items = (1..maxDay).map { it.toString() },
                                selectedIndex = clampedDay - 1,
                                onSelectIndex = { selectedDay = it + 1 },
                                modifier = Modifier.width(56.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.month), fontSize = 11.sp, color = AppGrayText)
                            WheelPicker(
                                items = months,
                                selectedIndex = selectedMonthIndex,
                                onSelectIndex = { selectedMonthIndex = it },
                                modifier = Modifier.width(130.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.year), fontSize = 11.sp, color = AppGrayText)
                            WheelPicker(
                                items = years,
                                selectedIndex = selectedYearIndex,
                                onSelectIndex = { selectedYearIndex = it },
                                modifier = Modifier.width(80.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
