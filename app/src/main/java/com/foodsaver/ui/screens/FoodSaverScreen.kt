package com.foodsaver.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foodsaver.R
import com.foodsaver.model.FoodEntry
import com.foodsaver.model.SortMode
import com.foodsaver.speech.SpeechCapturePhase
import com.foodsaver.ui.components.EditEntryDialog
import com.foodsaver.ui.components.SwipeToDeleteEntry
import com.foodsaver.ui.theme.AppBackground
import com.foodsaver.ui.theme.AppGrayText
import com.foodsaver.ui.theme.AppMicIdle
import com.foodsaver.ui.theme.AppPurple
import com.foodsaver.ui.theme.AppWhite
import com.foodsaver.util.fuzzyMatch

@Composable
fun FoodSaverScreen(
    capturePhase: SpeechCapturePhase,
    onToggle: () -> Unit,
    lastChunkText: String,
    entries: List<FoodEntry>,
    sortMode: SortMode,
    onToggleSort: () -> Unit,
    lastRemovedEntry: FoodEntry?,
    onRemoveEntry: (FoodEntry) -> Unit,
    onUndo: () -> Unit,
    onUpdateEntry: (FoodEntry, FoodEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editingEntry by remember { mutableStateOf<FoodEntry?>(null) }
    var searchFocused by remember { mutableStateOf(false) }

    val isPreparing = capturePhase == SpeechCapturePhase.PREPARING
    val isStopping = capturePhase == SpeechCapturePhase.STOPPING
    val micTransition = rememberInfiniteTransition(label = "micTransition")
    val preparingRotation =
        micTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "preparingRotation",
        )
    val preparingHueBlend =
        micTransition.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.62f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "preparingHueBlend",
        )
    val preparingScale =
        micTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.76f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "preparingScale",
        )
    val micIconTint by
        animateColorAsState(
            targetValue =
                when (capturePhase) {
                    SpeechCapturePhase.IDLE -> AppPurple
                    SpeechCapturePhase.PREPARING -> lerp(AppPurple, AppWhite, preparingHueBlend.value)
                    SpeechCapturePhase.LISTENING -> AppWhite
                    SpeechCapturePhase.STOPPING -> AppPurple.copy(alpha = 0.85f)
                },
            animationSpec = tween(durationMillis = 900),
            label = "micIconTint",
        )
    val micBackgroundColor by
        animateColorAsState(
            targetValue =
                when (capturePhase) {
                    SpeechCapturePhase.IDLE -> AppMicIdle
                    SpeechCapturePhase.PREPARING ->
                        lerp(AppWhite, AppPurple, preparingHueBlend.value)
                    SpeechCapturePhase.LISTENING -> AppPurple
                    SpeechCapturePhase.STOPPING -> lerp(AppPurple, AppMicIdle, 0.2f)
                },
            animationSpec = tween(durationMillis = 900),
            label = "micBackgroundColor",
        )
    val micBorderWidth by
        animateFloatAsState(
            targetValue = if (capturePhase == SpeechCapturePhase.IDLE) 6f else 0f,
            animationSpec = tween(durationMillis = 450),
            label = "micBorderWidth",
        )
    val micIconSize by
        animateFloatAsState(
            targetValue =
                when (capturePhase) {
                    SpeechCapturePhase.PREPARING -> 24f
                    SpeechCapturePhase.STOPPING -> 28f
                    else -> 32f
                },
            animationSpec = tween(durationMillis = 750),
            label = "micIconSize",
        )

    val sortedEntries =
        remember(entries, sortMode) {
            when (sortMode) {
                SortMode.ALPHA -> entries.sortedBy { it.name.lowercase() }
                SortMode.DATE -> entries.sortedBy { it.expiry }
            }
        }
    val visibleEntries =
        remember(sortedEntries, searchQuery) {
            sortedEntries.filter { fuzzyMatch(it.name, searchQuery) }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Undo button
                AnimatedVisibility(
                    visible = lastRemovedEntry != null,
                    enter =
                        scaleIn(
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                        ) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .padding(end = 16.dp)
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(AppPurple),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(
                            onClick = onUndo,
                            modifier = Modifier.size(52.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = stringResource(R.string.undo_entry_removal),
                                tint = AppWhite,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Mic button
                Box(
                    modifier =
                        Modifier
                            .padding(bottom = 4.dp, end = 16.dp)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(micBackgroundColor)
                            .then(
                                if (micBorderWidth <= 0f) {
                                    Modifier
                                } else {
                                    Modifier.border(micBorderWidth.dp, AppPurple, CircleShape)
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            imageVector =
                                if (capturePhase == SpeechCapturePhase.LISTENING) {
                                    Icons.Filled.Mic
                                } else {
                                    Icons.Filled.MicOff
                                },
                            contentDescription =
                                if (capturePhase == SpeechCapturePhase.IDLE) {
                                    stringResource(R.string.start_listening)
                                } else {
                                    stringResource(R.string.stop_listening)
                                },
                            tint = micIconTint,
                            modifier =
                                Modifier
                                    .size(micIconSize.dp)
                                    .graphicsLayer {
                                        rotationZ = if (isPreparing) preparingRotation.value else 0f
                                        val scale = if (isPreparing) preparingScale.value else 1f
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = if (isStopping) 0.8f else 1f
                                    },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
        ) {
            // Search bar and sort button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            fontSize = 14.sp,
                            color = AppGrayText,
                        )
                    },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = AppWhite,
                            unfocusedTextColor = AppWhite,
                            cursorColor = AppWhite,
                            focusedTrailingIconColor = AppWhite,
                            unfocusedTrailingIconColor = AppWhite.copy(alpha = 0.8f),
                        ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.clear_search),
                                    tint = AppWhite,
                                )
                            }
                        }
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged { searchFocused = it.isFocused }
                            .border(
                                2.dp,
                                if (searchFocused) {
                                    AppWhite
                                } else {
                                    AppWhite.copy(alpha = 0.65f)
                                },
                                RoundedCornerShape(50),
                            ),
                    shape = RoundedCornerShape(50),
                )
                Button(
                    onClick = onToggleSort,
                    colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.width(90.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SwapVert,
                        contentDescription =
                            if (sortMode == SortMode.ALPHA) {
                                stringResource(R.string.sort_alphabetically)
                            } else {
                                stringResource(R.string.sort_by_date)
                            },
                        tint = AppWhite,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier.width(34.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text =
                                if (sortMode == SortMode.ALPHA) {
                                    stringResource(R.string.sort_mode_alpha)
                                } else {
                                    stringResource(R.string.sort_mode_date)
                                },
                            color = AppWhite,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Last heard text (space always reserved)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (lastChunkText.isEmpty()) "" else "\"$lastChunkText\"",
                fontSize = 12.sp,
                color = AppGrayText,
                minLines = 1,
            )

            // Entries list
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(visibleEntries, key = { it.id }) { entry ->
                        SwipeToDeleteEntry(
                            entry = entry,
                            onRemove = { onRemoveEntry(entry) },
                            onEdit = { editingEntry = entry },
                            modifier =
                                Modifier.animateItem(
                                    placementSpec = tween(durationMillis = 350),
                                ),
                        )
                    }
                }

                // Top fade
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        listOf(AppBackground, Color.Transparent),
                                    ),
                            ),
                )

                // Bottom fade
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, AppBackground),
                                    ),
                            ),
                )
            }
        }

        // Edit overlay
        editingEntry?.let { entry ->
            EditEntryDialog(
                entry = entry,
                onDismiss = { updated ->
                    if (updated != entry) {
                        onUpdateEntry(entry, updated)
                    }
                    editingEntry = null
                },
            )
        }
    }
}
