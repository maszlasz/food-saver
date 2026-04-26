package com.foodsaver.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foodsaver.R
import com.foodsaver.ui.theme.AppCardDark
import com.foodsaver.ui.theme.AppGrayText
import com.foodsaver.ui.theme.AppPurple
import com.foodsaver.ui.theme.AppWhite

@Composable
fun ModelDownloadDialog(
    isDownloading: Boolean,
    isExtracting: Boolean,
    downloadProgress: Float,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = AppCardDark),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.speech_model_required),
                    color = AppWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.speech_model_download_message),
                    color = AppGrayText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.error_with_message, errorMessage),
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                when {
                    isDownloading -> {
                        val percent = (downloadProgress * 100).toInt()
                        Text(
                            text = stringResource(R.string.downloading_model, percent),
                            color = AppGrayText,
                            fontSize = 13.sp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppPurple,
                            trackColor = AppWhite.copy(alpha = 0.15f),
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }

                    isExtracting -> {
                        Text(
                            text = stringResource(R.string.extracting_model),
                            color = AppGrayText,
                            fontSize = 13.sp,
                        )
                    }

                    errorMessage != null -> {
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                        ) {
                            Text(stringResource(R.string.retry), color = AppWhite)
                        }
                    }

                    else -> {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = AppPurple),
                        ) {
                            Text(stringResource(R.string.download), color = AppWhite)
                        }
                    }
                }
            }
        }
    }
}
