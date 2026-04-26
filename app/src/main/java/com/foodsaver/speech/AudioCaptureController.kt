package com.foodsaver.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

internal class AudioCaptureController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val callbacks: Callbacks,
) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val READ_WINDOW_MS = 64
        private const val READ_SIZE_SAMPLES = SAMPLE_RATE * READ_WINDOW_MS / 1000
        private const val ITEM_SILENCE_MS = 600L
        private const val PREPARATION_MIN_MS = 500L
        private const val READY_TONE_GUARD_MS = 250L
        private const val TONE_DURATION_MS = 180
        private const val MAX_CALIBRATION_LEVEL = 0.05f
        private const val MIN_OPEN_THRESHOLD = 0.01f
        private const val MIN_CONTINUE_THRESHOLD = 0.005f
        private const val MAX_CHUNK_SAMPLES = SAMPLE_RATE * 15
    }

    data class Calibration(
        val averageBackgroundLevel: Float,
        val peakBackgroundLevel: Float,
        val speechOpenThreshold: Float,
        val speechContinueThreshold: Float,
    )

    data class Callbacks(
        val recognizerReadyState: () -> Boolean?,
        val onPreparingStarted: () -> Unit,
        val onListeningReady: (Calibration) -> Unit,
        val onPreparationFailed: () -> Unit,
        val onChunkCaptured: (FloatArray) -> Unit,
        val onCaptureStopped: () -> Unit,
    )

    private enum class CaptureStage {
        PREPARING,
        LISTENING,
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var readyToneGenerator: ToneGenerator? = null

    private val audioSamples = mutableListOf<Float>()
    private var chunkLevel = 0f

    @Volatile
    private var captureLoopEnabled = false

    @Volatile
    private var speechOpenThreshold = MIN_OPEN_THRESHOLD

    @Volatile
    private var speechContinueThreshold = MIN_CONTINUE_THRESHOLD

    @Volatile
    private var listeningReadyAtMs = 0L

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(): Boolean {
        if (captureLoopEnabled || recordingJob?.isActive == true) {
            return false
        }
        if (!hasRecordPermission()) {
            return false
        }

        val bufferSize =
            maxOf(
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ),
                READ_SIZE_SAMPLES * 8,
            )
        if (bufferSize <= 0) {
            return false
        }

        val recorder = createAudioRecord(bufferSize) ?: return false
        resetSessionState()
        audioRecord = recorder
        attachAudioEffects(recorder)

        try {
            recorder.startRecording()
        } catch (_: Exception) {
            captureLoopEnabled = false
            releaseRecorder(recorder)
            audioRecord = null
            return false
        }

        callbacks.onPreparingStarted()
        recordingJob = scope.launch(Dispatchers.IO) { captureLoop(recorder) }
        return true
    }

    fun stop() {
        if (!captureLoopEnabled && recordingJob == null) {
            callbacks.onCaptureStopped()
            return
        }
        captureLoopEnabled = false
        scope.launch(Dispatchers.IO) {
            recordingJob?.join()
            releaseRecorder(audioRecord)
            audioRecord = null
            recordingJob = null
            dispatchChunk()
            callbacks.onCaptureStopped()
        }
    }

    fun release() {
        captureLoopEnabled = false
        recordingJob?.cancel()
        recordingJob = null
        releaseRecorder(audioRecord)
        audioRecord = null
        readyToneGenerator?.release()
        readyToneGenerator = null
    }

    private fun captureLoop(recorder: AudioRecord) {
        val buffer = ShortArray(READ_SIZE_SAMPLES)
        var preparationFailed = false
        var stage = CaptureStage.PREPARING
        var speechActive = false
        var lastActivityTime = 0L
        val preparationStartedAt = System.currentTimeMillis()
        var backgroundLevelSum = 0f
        var peakBackgroundLevel = 0f
        var backgroundSamples = 0
        var pendingCalibration: Calibration? = null

        while (scope.coroutineContext.isActive && captureLoopEnabled) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read <= 0) {
                continue
            }

            val now = System.currentTimeMillis()
            val rms = computeRmsLevel(buffer, read)

            when (stage) {
                CaptureStage.PREPARING -> {
                    if (pendingCalibration != null) {
                        if (now < listeningReadyAtMs) {
                            continue
                        }

                        stage = CaptureStage.LISTENING
                        callbacks.onListeningReady(pendingCalibration)
                        pendingCalibration = null
                        continue
                    }

                    if (rms <= MAX_CALIBRATION_LEVEL) {
                        backgroundLevelSum += rms
                        peakBackgroundLevel = max(peakBackgroundLevel, rms)
                        backgroundSamples += 1
                    }

                    when (callbacks.recognizerReadyState()) {
                        false -> {
                            preparationFailed = true
                            captureLoopEnabled = false
                        }

                        true -> {
                            if (now - preparationStartedAt >= PREPARATION_MIN_MS) {
                                val averageBackgroundLevel =
                                    if (backgroundSamples > 0) {
                                        backgroundLevelSum / backgroundSamples
                                    } else {
                                        0f
                                    }

                                speechOpenThreshold =
                                    maxOf(
                                        MIN_OPEN_THRESHOLD,
                                        averageBackgroundLevel * 3f,
                                        peakBackgroundLevel * 1.5f,
                                    )
                                speechContinueThreshold =
                                    maxOf(
                                        MIN_CONTINUE_THRESHOLD,
                                        averageBackgroundLevel * 2.5f,
                                        peakBackgroundLevel * 1.25f,
                                    )

                                synchronized(audioSamples) {
                                    audioSamples.clear()
                                    chunkLevel = 0f
                                }

                                speechActive = false
                                lastActivityTime = 0L
                                listeningReadyAtMs = now + READY_TONE_GUARD_MS
                                pendingCalibration =
                                    Calibration(
                                        averageBackgroundLevel,
                                        peakBackgroundLevel,
                                        speechOpenThreshold,
                                        speechContinueThreshold,
                                    )
                                playReadyTone()
                            }
                        }

                        null -> {}
                    }
                }

                CaptureStage.LISTENING -> {
                    val countsAsSpeech =
                        if (speechActive) {
                            rms >= speechContinueThreshold
                        } else {
                            rms >= speechOpenThreshold
                        }

                    val currentSize =
                        if (countsAsSpeech || speechActive) {
                            synchronized(audioSamples) {
                                repeat(read) { index ->
                                    audioSamples.add(buffer[index].toFloat() / 32768f)
                                }
                                chunkLevel = max(chunkLevel, rms)
                                audioSamples.size
                            }
                        } else {
                            0
                        }

                    if (countsAsSpeech) {
                        speechActive = true
                        lastActivityTime = now
                    } else if (speechActive && now - lastActivityTime >= ITEM_SILENCE_MS) {
                        dispatchChunk()
                        speechActive = false
                    }

                    if (currentSize >= MAX_CHUNK_SAMPLES) {
                        dispatchChunk()
                        speechActive = false
                        lastActivityTime = now
                    }
                }
            }
        }

        if (preparationFailed) {
            releaseRecorder(recorder)
            if (audioRecord === recorder) {
                audioRecord = null
            }
            recordingJob = null
            callbacks.onPreparationFailed()
        }
    }

    private fun resetSessionState() {
        synchronized(audioSamples) {
            audioSamples.clear()
            chunkLevel = 0f
        }
        speechOpenThreshold = MIN_OPEN_THRESHOLD
        speechContinueThreshold = MIN_CONTINUE_THRESHOLD
        listeningReadyAtMs = 0L
        captureLoopEnabled = true
    }

    private fun dispatchChunk() {
        val samples: FloatArray
        val level: Float
        synchronized(audioSamples) {
            samples = audioSamples.toFloatArray()
            level = chunkLevel
            audioSamples.clear()
            chunkLevel = 0f
        }
        if (samples.size < SAMPLE_RATE / 8) {
            return
        }
        if (level < speechContinueThreshold) {
            return
        }
        callbacks.onChunkCaptured(samples)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun createAudioRecord(bufferSize: Int): AudioRecord? {
        if (!hasRecordPermission()) {
            return null
        }

        val sources =
            listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)
                .distinct()
        for (source in sources) {
            val recorder =
                try {
                    AudioRecord(
                        source,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize,
                    )
                } catch (_: Exception) {
                    null
                }

            if (recorder != null && recorder.state == AudioRecord.STATE_INITIALIZED) {
                return recorder
            }

            recorder?.release()
        }

        return null
    }

    private fun attachAudioEffects(recorder: AudioRecord) {
        if (!hasRecordPermission()) {
            return
        }
        releaseAudioEffects()
        val sessionId = recorder.audioSessionId

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor =
                NoiseSuppressor.create(sessionId)?.also {
                    runCatching { it.enabled = true }
                }
        }

        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler =
                AcousticEchoCanceler.create(sessionId)?.also {
                    runCatching { it.enabled = true }
                }
        }

        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl =
                AutomaticGainControl.create(sessionId)?.also {
                    runCatching { it.enabled = true }
                }
        }
    }

    private fun releaseRecorder(recorder: AudioRecord?) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        releaseAudioEffects()
    }

    private fun releaseAudioEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { acousticEchoCanceler?.release() }
        runCatching { automaticGainControl?.release() }
        noiseSuppressor = null
        acousticEchoCanceler = null
        automaticGainControl = null
    }

    private fun playReadyTone() {
        val tone =
            readyToneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 85).also {
                readyToneGenerator = it
            }
        runCatching { tone.startTone(ToneGenerator.TONE_CDMA_CONFIRM, TONE_DURATION_MS) }
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun computeRmsLevel(
        buffer: ShortArray,
        read: Int,
    ): Float {
        var sumSquares = 0.0
        repeat(read) { index ->
            val sample = buffer[index].toDouble() / 32768.0
            sumSquares += sample * sample
        }
        return sqrt(sumSquares / read).toFloat()
    }
}
