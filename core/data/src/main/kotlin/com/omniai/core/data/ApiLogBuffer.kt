package com.omniai.core.data

import com.omniai.core.common.Redactor
import com.omniai.core.provider.http.ApiLogSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory, redacted API log (method, URL without query, status, timing). Never persisted. */
@Singleton
class ApiLogBuffer @Inject constructor() : ApiLogSink {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun log(line: String) {
        val entry = "${format.format(Date())}  ${Redactor.redact(line)}"
        _lines.update { (it + entry).takeLast(MAX) }
    }

    fun clear() { _lines.value = emptyList() }

    private companion object { const val MAX = 300 }
}
