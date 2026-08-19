package com.vibecode.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.Instant

object AppLog {
    private const val MAX_LINES = 600
    private val lock = Any()
    private var file: File? = null
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun init(context: Context) {
        synchronized(lock) {
            if (file != null) return
            file = File(context.applicationContext.filesDir, "vibecode-diagnostics.log")
            _lines.value = runCatching { file!!.readLines().takeLast(MAX_LINES) }.getOrDefault(emptyList())
            addLocked("INFO", "app", "diagnostic log initialized")
        }
    }

    fun add(level: String, source: String, message: String) {
        synchronized(lock) {
            addLocked(level, source, sanitize(message))
        }
    }

    fun clear() {
        synchronized(lock) {
            runCatching { file?.writeText("") }
            _lines.value = emptyList()
        }
    }

    private fun addLocked(level: String, source: String, message: String) {
        val line = "${Instant.now()} ${level.uppercase()} [$source] $message"
        val next = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = next
        runCatching {
            val f = file ?: return@runCatching
            f.parentFile?.mkdirs()
            f.writeText(next.joinToString("\n", postfix = if (next.isEmpty()) "" else "\n"))
        }
    }

    private fun sanitize(raw: String): String {
        val withoutQueryToken = Regex("([?&]token=)[^&\\s]+", RegexOption.IGNORE_CASE)
            .replace(raw) { match -> match.groupValues[1] + "<redacted>" }
        return Regex("(Bearer\\s+)[A-Za-z0-9._~+/-]+", RegexOption.IGNORE_CASE)
            .replace(withoutQueryToken) { match -> match.groupValues[1] + "<redacted>" }
            .take(4000)
    }
}
