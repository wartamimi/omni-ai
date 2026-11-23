package com.omniai.core.provider.http

import okio.BufferedSource

/** One Server-Sent Event. */
data class SseEvent(val event: String?, val data: String, val id: String?)

/**
 * Incremental Server-Sent Events parser (WHATWG event-stream format).
 * Reads line by line so text can be rendered as soon as each event arrives.
 */
class SseParser(private val source: BufferedSource) {
    private var eventName: String? = null
    private var lastId: String? = null
    private val data = StringBuilder()
    private var hasData = false

    /** Returns the next event, or null at end of stream. Blocking; call from an IO thread. */
    fun next(): SseEvent? {
        while (true) {
            val line = source.readUtf8Line() ?: return flush()
            if (line.isEmpty()) {
                val event = flush()
                if (event != null) return event
                continue
            }
            if (line.startsWith(":")) continue // comment / keep-alive
            val colon = line.indexOf(':')
            val field = if (colon == -1) line else line.substring(0, colon)
            var value = if (colon == -1) "" else line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
            when (field) {
                "event" -> eventName = value
                "data" -> {
                    if (hasData) data.append('\n')
                    data.append(value)
                    hasData = true
                }
                "id" -> lastId = value
                else -> Unit // "retry" and unknown fields are ignored
            }
        }
    }

    private fun flush(): SseEvent? {
        if (!hasData) {
            eventName = null
            return null
        }
        val event = SseEvent(eventName, data.toString(), lastId)
        data.setLength(0)
        hasData = false
        eventName = null
        return event
    }
}
