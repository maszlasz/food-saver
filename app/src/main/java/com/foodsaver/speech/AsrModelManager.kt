package com.foodsaver.speech

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object AsrModelManager {
    const val MODEL_DIR_NAME = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"

    private const val ARCHIVE_NAME = "$MODEL_DIR_NAME.tar.bz2"
    private const val DOWNLOAD_URL =
        "https://github.com/maszlasz/food-saver/releases/download/asr-model/$ARCHIVE_NAME"

    fun isModelPresent(filesDir: File): Boolean {
        val modelDirectory = File(filesDir, MODEL_DIR_NAME)
        return modelDirectory.isDirectory && installationMarker(filesDir).exists()
    }

    fun downloadAndExtract(
        filesDir: File,
        onProgress: (Float) -> Unit,
        onExtracting: () -> Unit,
    ) {
        val archiveFile = File(filesDir, ARCHIVE_NAME)
        val installedMarker = installationMarker(filesDir)
        try {
            installedMarker.delete()
            download(archiveFile, onProgress)
            onExtracting()
            extract(archiveFile, filesDir)
            installedMarker.writeText("done")
        } finally {
            archiveFile.delete()
        }
    }

    private fun download(
        destination: File,
        onProgress: (Float) -> Unit,
    ) {
        val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw RuntimeException("Pobieranie nie powiodło się (HTTP ${connection.responseCode})")
        }

        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        onProgress(downloadedBytes.toFloat() / totalBytes)
                    }
                }
            }
        }
    }

    private fun extract(
        archive: File,
        destinationDirectory: File,
    ) {
        val canonicalDestinationDirectory = destinationDirectory.canonicalFile
        val destinationRootPath = canonicalDestinationDirectory.path + File.separator
        BufferedInputStream(archive.inputStream()).use { bufferedStream ->
            BZip2CompressorInputStream(bufferedStream).use { bzipStream ->
                TarArchiveInputStream(bzipStream).use { tarStream ->
                    var entry = tarStream.nextEntry
                    while (entry != null) {
                        val outputFile =
                            File(canonicalDestinationDirectory, entry.name).canonicalFile
                        val isInsideDestination =
                            outputFile.path == canonicalDestinationDirectory.path ||
                                outputFile.path.startsWith(destinationRootPath)
                        if (!isInsideDestination) {
                            entry = tarStream.nextEntry
                            continue
                        }

                        if (entry.isDirectory) {
                            outputFile.mkdirs()
                        } else {
                            outputFile.parentFile?.mkdirs()
                            FileOutputStream(outputFile).use { output -> tarStream.copyTo(output) }
                        }

                        entry = tarStream.nextEntry
                    }
                }
            }
        }
    }

    private fun installationMarker(filesDir: File): File {
        val modelDirectory = File(filesDir, MODEL_DIR_NAME)
        return File(modelDirectory, ".installed")
    }
}
