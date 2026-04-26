package com.foodsaver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.foodsaver.speech.SpeechCapturePhase
import com.foodsaver.ui.components.ModelDownloadDialog
import com.foodsaver.ui.screens.FoodSaverScreen
import com.foodsaver.ui.theme.FoodSaverTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val viewModel: FoodSaverViewModel = viewModel()
            val requiredPermissions = remember { runtimePermissions() }
            val permissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    if (hasAllRequiredPermissions(requiredPermissions)) {
                        viewModel.toggleListening()
                    }
                }

            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val downloadUiState by viewModel.downloadUiState.collectAsStateWithLifecycle()

            FoodSaverTheme {
                FoodSaverScreen(
                    capturePhase = uiState.capturePhase,
                    onToggle = {
                        when (uiState.capturePhase) {
                            SpeechCapturePhase.IDLE -> {
                                if (hasAllRequiredPermissions(requiredPermissions)) {
                                    viewModel.toggleListening()
                                } else {
                                    val missingPermissions = missingRequiredPermissions(requiredPermissions)
                                    permissionLauncher.launch(missingPermissions.toTypedArray())
                                }
                            }
                            SpeechCapturePhase.PREPARING,
                            SpeechCapturePhase.LISTENING -> viewModel.toggleListening()
                            SpeechCapturePhase.STOPPING -> Unit
                        }
                    },
                    lastChunkText = uiState.lastChunkText,
                    entries = uiState.entries,
                    sortMode = uiState.sortMode,
                    onToggleSort = viewModel::toggleSort,
                    lastRemovedEntry = uiState.lastRemoved?.first,
                    onRemoveEntry = viewModel::removeEntry,
                    onUndo = viewModel::undoRemoval,
                    onUpdateEntry = viewModel::updateEntry,
                )

                if (downloadUiState.showDialog) {
                    ModelDownloadDialog(
                        isDownloading = downloadUiState.isDownloading,
                        isExtracting = downloadUiState.isExtracting,
                        downloadProgress = downloadUiState.progress,
                        errorMessage = downloadUiState.errorMessage,
                        onConfirm = viewModel::startDownload,
                        onRetry = viewModel::startDownload,
                    )
                }
            }
        }
    }

    private fun runtimePermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions
    }

    private fun hasAllRequiredPermissions(requiredPermissions: List<String>): Boolean =
        missingRequiredPermissions(requiredPermissions).isEmpty()

    private fun missingRequiredPermissions(requiredPermissions: List<String>): List<String> =
        requiredPermissions.filterNot(::hasPermission)

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }
}
