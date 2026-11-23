package com.omniai.core.provider

import com.omniai.core.provider.http.SseParser
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SseParserTest {
    @Test fun `parses events, multi-line data, comments and names`() {
        val stream = ": keep-alive\n\nevent: message_start\ndata: {\"a\":1}\n\ndata: line1\ndata: line2\n\ndata:[DONE]\n\n"
        val parser = SseParser(Buffer().writeUtf8(stream))
        val e1 = parser.next()!!
        assertEquals("message_start", e1.event)
        assertEquals("{\"a\":1}", e1.data)
        assertEquals("line1\nline2", parser.next()!!.data)
        assertEquals("[DONE]", parser.next()!!.data)
        assertNull(parser.next())
    }

    @Test fun `flushes final event without trailing blank line`() {
        val parser = SseParser(Buffer().writeUtf8("data: tail"))
        assertEquals("tail", parser.next()!!.data)
        assertNull(parser.next())
    }
}
