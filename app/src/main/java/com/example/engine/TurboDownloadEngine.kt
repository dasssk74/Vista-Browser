package com.example.engine

import android.content.Context
import android.net.Uri
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TurboDownloadEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, Job>()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _downloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloads: StateFlow<List<DownloadTask>> = _downloads.asStateFlow()

    fun enqueueDownload(
        url: String,
        suggestedFileName: String? = null,
        threadCount: Int = 8
    ): String {
        val id = UUID.randomUUID().toString()
        val resolvedName = suggestedFileName?.takeIf { it.isNotBlank() }
            ?: inferFileNameFromUrl(url)

        val downloadsDir = File(context.filesDir, "turbo_downloads").apply { mkdirs() }
        val destinationFile = File(downloadsDir, resolvedName)

        val initialTask = DownloadTask(
            id = id,
            fileName = resolvedName,
            url = url,
            totalBytes = -1L,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            activeThreads = threadCount.coerceIn(1, 16),
            status = DownloadStatus.CONNECTING,
            localPath = destinationFile.absolutePath
        )

        _downloads.update { listOf(initialTask) + it }

        val job = scope.launch {
            executeMultiThreadDownload(initialTask, destinationFile, threadCount.coerceIn(1, 16))
        }
        activeJobs[id] = job
        return id
    }

    fun cancelOrRemoveDownload(downloadId: String) {
        activeJobs.remove(downloadId)?.cancel()
        _downloads.update { list -> list.filterNot { it.id == downloadId } }
    }

    private suspend fun executeMultiThreadDownload(
        task: DownloadTask,
        destinationFile: File,
        requestedThreads: Int
    ) {
        try {
            // Probe file headers for Content-Length and Accept-Ranges
            var contentLength = -1L
            var supportsRanges = false

            try {
                val headReq = Request.Builder()
                    .url(task.url)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Android; AegisTurboDownloader/1.0)")
                    .build()
                okHttpClient.newCall(headReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                        val acceptRanges = resp.header("Accept-Ranges") ?: ""
                        supportsRanges = acceptRanges.contains("bytes", ignoreCase = true) && contentLength > 64 * 1024L
                    }
                }
            } catch (e: Exception) {
                // Fallback to GET if HEAD is rejected
            }

            val totalBytesCounter = AtomicLong(0L)
            val actualThreads = if (supportsRanges && contentLength > 0) requestedThreads else 1

            updateTask(task.id) {
                it.copy(
                    totalBytes = contentLength,
                    activeThreads = actualThreads,
                    status = DownloadStatus.DOWNLOADING
                )
            }

            // Launch speed ticker coroutine
            val tickerJob = scope.launch {
                var previousBytes = 0L
                while (isActive) {
                    delay(400L)
                    val current = totalBytesCounter.get()
                    val delta = (current - previousBytes).coerceAtLeast(0L)
                    previousBytes = current
                    val bytesPerSec = (delta * 1000L) / 400L
                    updateTask(task.id) {
                        it.copy(
                            downloadedBytes = current,
                            speedBytesPerSec = bytesPerSec
                        )
                    }
                }
            }

            try {
                if (supportsRanges && contentLength > 0) {
                    RandomAccessFile(destinationFile, "rw").use { raf ->
                        raf.setLength(contentLength)
                    }
                    val chunkSize = contentLength / actualThreads
                    coroutineScope {
                        val workers = (0 until actualThreads).map { index ->
                            val startByte = index * chunkSize
                            val endByte = if (index == actualThreads - 1) contentLength - 1 else (startByte + chunkSize - 1)
                            async(Dispatchers.IO) {
                                downloadByteRange(task.url, destinationFile, startByte, endByte, totalBytesCounter)
                            }
                        }
                        workers.awaitAll()
                    }
                } else {
                    downloadSingleStream(task.url, destinationFile, totalBytesCounter) { discoveredLength ->
                        if (discoveredLength > 0) {
                            updateTask(task.id) { it.copy(totalBytes = discoveredLength) }
                        }
                    }
                }
            } finally {
                tickerJob.cancel()
            }

            val finalBytes = totalBytesCounter.get().coerceAtLeast(destinationFile.length())
            updateTask(task.id) {
                it.copy(
                    totalBytes = if (it.totalBytes > 0) it.totalBytes else finalBytes,
                    downloadedBytes = finalBytes,
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.COMPLETED
                )
            }
        } catch (e: Exception) {
            updateTask(task.id) {
                it.copy(
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.FAILED
                )
            }
        } finally {
            activeJobs.remove(task.id)
        }
    }

    private fun downloadByteRange(
        url: String,
        file: File,
        startByte: Long,
        endByte: Long,
        counter: AtomicLong
    ) {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$startByte-$endByte")
            .header("User-Agent", "Mozilla/5.0 (Android; AegisTurboDownloader/1.0)")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Empty response body")
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(startByte)
                val buffer = ByteArray(32 * 1024)
                body.byteStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        raf.write(buffer, 0, read)
                        counter.addAndGet(read.toLong())
                    }
                }
            }
        }
    }

    private fun downloadSingleStream(
        url: String,
        file: File,
        counter: AtomicLong,
        onContentLength: (Long) -> Unit
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android; AegisTurboDownloader/1.0)")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw IllegalStateException("Empty response body")
            onContentLength(body.contentLength())
            file.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                body.byteStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        counter.addAndGet(read.toLong())
                    }
                }
            }
        }
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _downloads.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    companion object {
        fun inferFileNameFromUrl(url: String): String {
            return try {
                val lastSegment = Uri.parse(url).lastPathSegment
                if (!lastSegment.isNullOrBlank() && lastSegment.contains(".")) {
                    lastSegment.take(64)
                } else {
                    "aegis_download_${System.currentTimeMillis() % 10000}.bin"
                }
            } catch (e: Exception) {
                "aegis_file_${System.currentTimeMillis() % 10000}.dat"
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format("%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format("%.2f MB", mb)
            val gb = mb / 1024.0
            return String.format("%.2f GB", gb)
        }
    }
}
