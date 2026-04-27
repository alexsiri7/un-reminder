package net.interstellarai.unreminder.service.log

import android.os.Process

private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")
private val IP_REGEX = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")

fun collectLogs(maxLines: Int = 200): String? = runCatching {
    val pid = Process.myPid().toString()
    val process = ProcessBuilder("logcat", "--pid=$pid", "-t", maxLines.toString())
        .redirectErrorStream(true)
        .start()
    val raw = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    process.waitFor()
    anonymizeLogs(raw).trim().takeIf { it.isNotEmpty() }
}.getOrNull()

fun anonymizeLogs(raw: String): String = raw
    .replace(EMAIL_REGEX, "[EMAIL]")
    .replace(IP_REGEX, "[IP]")
