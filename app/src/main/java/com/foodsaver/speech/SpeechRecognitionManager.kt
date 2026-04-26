package com.foodsaver.speech

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import com.foodsaver.R
import com.foodsaver.model.FoodEntry
import com.foodsaver.util.PolishDateUtils
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class SpeechRecognitionManager(
    private val context: Context,
) {
    companion object {
        private const val SAMPLE_RATE = 16000
    }

    private val _capturePhase = MutableStateFlow(SpeechCapturePhase.IDLE)
    val capturePhase: StateFlow<SpeechCapturePhase> = _capturePhase.asStateFlow()

    private val _lastChunkText = MutableStateFlow("")
    val lastChunkText: StateFlow<String> = _lastChunkText.asStateFlow()

    var onEntriesParsed: (List<FoodEntry>) -> Unit = {}

    private var recognizer: OfflineRecognizer? = null
    private val recognizerReadyState = MutableStateFlow<Boolean?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val decodingDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val audioChannel = Channel<FloatArray>(Channel.UNLIMITED)
    private val pendingWords = mutableListOf<String>()
    private val pendingDecodeCount = AtomicInteger(0)
    private val sessionLock = Any()

    @Volatile
    private var waitingForDrain = false

//    match non-alphanumeric
    private val tokenCleanupRegex = Regex("[^\\p{L}\\p{N}]")

    private val hotwordsPath: String = ensureHotwordsFile()
    private val audioCaptureController =
        AudioCaptureController(
            context = context,
            scope = scope,
            callbacks =
                AudioCaptureController.Callbacks(
                    recognizerReadyState = { recognizerReadyState.value },
                    onPreparingStarted = {
                        setCapturePhase(SpeechCapturePhase.PREPARING)
                    },
                    onListeningReady = {
                        setCapturePhase(SpeechCapturePhase.LISTENING)
                    },
                    onPreparationFailed = {
                        setCapturePhase(SpeechCapturePhase.STOPPING)
                        synchronized(sessionLock) {
                            waitingForDrain = true
                        }
                        maybeFinalizeStoppedSession()
                    },
                    onChunkCaptured = ::enqueueChunk,
                    onCaptureStopped = {
                        synchronized(sessionLock) {
                            waitingForDrain = true
                        }
                        maybeFinalizeStoppedSession()
                    },
                ),
        )

    init {
        scope.launch(decodingDispatcher) { processEvents() }
    }

    fun initializeRecognizer() {
        scope.launch(decodingDispatcher) {
            try {
                recognizer?.release()
                recognizer = null
                val modelPath =
                    File(context.filesDir, AsrModelManager.MODEL_DIR_NAME).absolutePath
                val config =
                    OfflineRecognizerConfig(
                        featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                        modelConfig =
                            OfflineModelConfig(
                                transducer =
                                    OfflineTransducerModelConfig(
                                        encoder = "$modelPath/encoder.int8.onnx",
                                        decoder = "$modelPath/decoder.int8.onnx",
                                        joiner = "$modelPath/joiner.int8.onnx",
                                    ),
                                tokens = "$modelPath/tokens.txt",
                                numThreads = recognizerThreadCount(),
                                modelingUnit = "bpe",
                                bpeVocab = "$modelPath/bpe.vocab",
                            ),
                        decodingMethod = "modified_beam_search",
                        hotwordsFile = hotwordsPath,
                    )

                recognizer = OfflineRecognizer(config = config)
                recognizerReadyState.value = true
            } catch (_: Exception) {
                recognizerReadyState.value = false
            }
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun toggleListening() {
        when (_capturePhase.value) {
            SpeechCapturePhase.IDLE -> startRecording()
            SpeechCapturePhase.PREPARING, SpeechCapturePhase.LISTENING -> stopRecording()
            SpeechCapturePhase.STOPPING -> Unit
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        if (_capturePhase.value != SpeechCapturePhase.IDLE) {
            return
        }
        synchronized(pendingWords) {
            pendingWords.clear()
        }
        synchronized(sessionLock) {
            waitingForDrain = false
        }
        if (!audioCaptureController.start()) {
            setCapturePhase(SpeechCapturePhase.IDLE)
        }
    }

    private fun stopRecording() {
        if (_capturePhase.value == SpeechCapturePhase.IDLE ||
            _capturePhase.value == SpeechCapturePhase.STOPPING
        ) {
            return
        }
        setCapturePhase(SpeechCapturePhase.STOPPING)
        audioCaptureController.stop()
    }

    private fun enqueueChunk(samples: FloatArray) {
        pendingDecodeCount.incrementAndGet()
        val result = audioChannel.trySend(samples)
        if (result.isFailure) {
            pendingDecodeCount.decrementAndGet()
            maybeFinalizeStoppedSession()
        }
    }

    private fun ensureHotwordsFile(): String {
        val outFile = File(context.filesDir, "hotwords.txt")
        context.assets.open("hotwords.txt").use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
        return outFile.absolutePath
    }

    private fun recognizerThreadCount(): Int =
        max(1, Runtime.getRuntime().availableProcessors() - 1)

    private suspend fun processEvents() {
        for (samples in audioChannel) {
            try {
                val rec = recognizer ?: continue

                val stream = rec.createStream()
                stream.acceptWaveform(samples = samples, sampleRate = SAMPLE_RATE)
                rec.decode(stream)
                val text = rec.getResult(stream).text.trim()
                stream.release()

                if (text.isBlank()) {
                    continue
                }

                val chunkWords =
                    text
                        .lowercase()
                        .split(Regex("\\s+"))
                        .map(::sanitizeToken)
                        .filter { it.isNotEmpty() }
                        .map(::sanitizeToken)
                        .filter { it.isNotEmpty() }

                val newEntries = mutableListOf<FoodEntry>()

                synchronized(pendingWords) {
                    for (word in chunkWords) {
                        val month = PolishDateUtils.resolveMonth(word)
                        if (month != null && pendingWords.size >= 2) {
                            val entry = PolishEntryParser.parseSegment(pendingWords, month)
                            if (entry != null) {
                                newEntries.add(entry)
                                pendingWords.clear()
                            } else {
                                pendingWords.add(word)
                            }
                        } else {
                            pendingWords.add(word)
                        }
                    }
                }

                if (newEntries.isNotEmpty()) {
                    _lastChunkText.value = text
                    withContext(Dispatchers.Main) {
                        onEntriesParsed(newEntries)
                    }
                }
            } catch (_: Exception) {
            } finally {
                pendingDecodeCount.decrementAndGet()
                maybeFinalizeStoppedSession()
            }
        }
    }

    private fun setCapturePhase(phase: SpeechCapturePhase) {
        _capturePhase.value = phase
    }

    private fun maybeFinalizeStoppedSession() {
        synchronized(sessionLock) {
            if (!waitingForDrain || pendingDecodeCount.get() != 0) {
                return
            }
            waitingForDrain = false
        }

        synchronized(pendingWords) {
            if (pendingWords.isNotEmpty()) {
                _lastChunkText.value =
                    context.getString(
                        R.string.incomplete_phrase,
                        pendingWords.joinToString(" "),
                    )
                pendingWords.clear()
            }
        }
        setCapturePhase(SpeechCapturePhase.IDLE)
    }

    private fun sanitizeToken(token: String): String = token.replace(tokenCleanupRegex, "")

    fun release() {
        setCapturePhase(SpeechCapturePhase.IDLE)
        audioCaptureController.release()
        recognizer?.release()
        audioChannel.close()
        scope.cancel()
    }
}
