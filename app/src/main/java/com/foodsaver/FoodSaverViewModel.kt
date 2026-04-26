package com.foodsaver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodsaver.data.FoodEntryRepository
import com.foodsaver.model.FoodEntry
import com.foodsaver.model.SortMode
import com.foodsaver.notification.NotificationScheduler
import com.foodsaver.speech.AsrModelManager
import com.foodsaver.speech.SpeechCapturePhase
import com.foodsaver.speech.SpeechRecognitionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FoodSaverUiState(
    val entries: List<FoodEntry> = emptyList(),
    val sortMode: SortMode = SortMode.DATE,
    val lastRemoved: Pair<FoodEntry, Int>? = null,
    val capturePhase: SpeechCapturePhase = SpeechCapturePhase.IDLE,
    val lastChunkText: String = "",
)

data class DownloadUiState(
    val showDialog: Boolean = false,
    val isDownloading: Boolean = false,
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val errorMessage: String? = null,
)

class FoodSaverViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val context: Application
        get() = getApplication()

    private val speechManager = SpeechRecognitionManager(application)

    private val _uiState = MutableStateFlow(FoodSaverUiState())
    val uiState: StateFlow<FoodSaverUiState> = _uiState.asStateFlow()

    private val _downloadUiState = MutableStateFlow(DownloadUiState())
    val downloadUiState: StateFlow<DownloadUiState> = _downloadUiState.asStateFlow()

    fun toggleListening() = speechManager.toggleListening()

    init {

        viewModelScope.launch {
            speechManager.capturePhase.collectLatest { capturePhase ->
                _uiState.update { it.copy(capturePhase = capturePhase) }
            }
        }

        viewModelScope.launch {
            speechManager.lastChunkText.collectLatest { lastChunkText ->
                _uiState.update { it.copy(lastChunkText = lastChunkText) }
            }
        }

        viewModelScope.launch {
            val allEntries = FoodEntryRepository.entriesFlow(context).first()
            val today = LocalDate.now()
            val activeEntries = allEntries.filter { !it.expiry.isBefore(today) }
            _uiState.update { it.copy(entries = activeEntries) }
            if (activeEntries.size < allEntries.size) {
                FoodEntryRepository.save(context, activeEntries)
            }
        }

        if (AsrModelManager.isModelPresent(context.filesDir)) {
            speechManager.initializeRecognizer()
        } else {
            _downloadUiState.update { it.copy(showDialog = true) }
        }

        speechManager.onEntriesParsed = { newEntries ->
            _uiState.update { it.copy(entries = it.entries + newEntries) }
            save()
            newEntries.forEach { NotificationScheduler.schedule(context, it) }
        }
    }

    fun toggleSort() {
        _uiState.update { currentState ->
            currentState.copy(
                sortMode =
                    if (currentState.sortMode == SortMode.ALPHA) {
                        SortMode.DATE
                    } else {
                        SortMode.ALPHA
                    },
            )
        }
    }

    fun removeEntry(entry: FoodEntry) {
        val updatedEntries = uiState.value.entries.toMutableList()
        val index = updatedEntries.indexOf(entry)
        if (index < 0) {
            return
        }

        updatedEntries.removeAt(index)
        _uiState.update {
            it.copy(
                entries = updatedEntries,
                lastRemoved = entry to index,
            )
        }
        save()
        NotificationScheduler.cancelAlarms(context, entry)
    }

    fun undoRemoval() {
        val (entry, index) = uiState.value.lastRemoved ?: return
        val updatedEntries = uiState.value.entries.toMutableList()
        updatedEntries.add(index.coerceIn(0, updatedEntries.size), entry)
        _uiState.update {
            it.copy(
                entries = updatedEntries,
                lastRemoved = null,
            )
        }
        save()
        NotificationScheduler.schedule(context, entry)
    }

    fun updateEntry(
        old: FoodEntry,
        new: FoodEntry,
    ) {
        val updatedEntries = uiState.value.entries.toMutableList()
        val index = updatedEntries.indexOf(old)
        if (index < 0) {
            return
        }

        updatedEntries[index] = new
        _uiState.update { it.copy(entries = updatedEntries) }
        save()
        NotificationScheduler.cancelAlarms(context, old)
        NotificationScheduler.schedule(context, new)
    }

    fun startDownload() {
        _downloadUiState.update {
            it.copy(
                isDownloading = true,
                isExtracting = false,
                progress = 0f,
                errorMessage = null,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                AsrModelManager.downloadAndExtract(
                    context.filesDir,
                    onProgress = { progress ->
                        _downloadUiState.update { it.copy(progress = progress) }
                    },
                    onExtracting = {
                        _downloadUiState.update {
                            it.copy(
                                isDownloading = false,
                                isExtracting = true,
                                progress = 1f,
                            )
                        }
                    },
                )
                _downloadUiState.update {
                    it.copy(
                        showDialog = false,
                        isDownloading = false,
                        isExtracting = false,
                        progress = 0f,
                        errorMessage = null,
                    )
                }
                speechManager.initializeRecognizer()
            } catch (_: Exception) {
                _downloadUiState.update {
                    it.copy(
                        isDownloading = false,
                        isExtracting = false,
                        errorMessage = context.getString(R.string.unknown_error),
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.release()
    }

    private fun save() {
        viewModelScope.launch {
            FoodEntryRepository.save(context, uiState.value.entries)
        }
    }
}
