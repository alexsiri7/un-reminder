package net.interstellarai.unreminder.service.log

import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val TAG = "LogCollector"

private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
private val IPV4_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")

// Matches full form (7 colons) and compressed form (::). Requires :: to avoid
// false-positives with timestamps (HH:MM:SS) which never contain double-colon.
private val IPV6_REGEX = Regex(
    """(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|""" +
    """(?:[0-9a-fA-F]{1,4}:)*::(?:[0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{0,4}"""
)

fun collectLogs(maxLines: Int = 200): String? {
    return try {
        val pid = Process.myPid().toString()
        val process = ProcessBuilder("logcat", "--pid=$pid", "-t", maxLines.toString())
            .redirectErrorStream(true)
            .start()
        val raw = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            Log.w(TAG, "collectLogs: logcat process timed out, destroyed")
            return null
        }
        processLogOutput(raw)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "collectLogs failed", e)
        null
    }
}

internal fun processLogOutput(raw: String): String? =
    anonymizeLogs(raw).trim().takeIf { it.isNotEmpty() }

fun anonymizeLogs(raw: String): String = raw
    .replace(EMAIL_REGEX, "[EMAIL]")
    .replace(IPV4_REGEX, "[IP]")
    .replace(IPV6_REGEX, "[IP]")
