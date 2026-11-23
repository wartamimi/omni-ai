package com.omniai.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactorTest {
    @Test
    fun `redacts bearer tokens and api key formats`() {
        val text = "Authorization: Bearer sk-proj-abcdefghijklmnopqrstuvwx and key AIzaSyA1234567890abcdefghijklmnopqrstu"
        val out = Redactor.redact(text)
        assertFalse(out.contains("abcdefghijklmnopqrstuvwx"))
        assertFalse(out.contains("AIzaSyA1234567890"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `redacts query parameter keys and explicit secrets`() {
        val out = Redactor.redact("GET https://x.test/v1?key=supersecretvalue&x=1 secretABC", listOf("secretABC"))
        assertFalse(out.contains("supersecretvalue"))
        assertFalse(out.contains("secretABC"))
    }

    @Test
    fun `redacts json api key fields`() {
        val out = Redactor.redact("""{"api_key": "abc123456789", "x-api-key":"zzz999999999"}""")
        assertFalse(out.contains("abc123456789"))
        assertFalse(out.contains("zzz999999999"))
    }

    @Test
    fun `mask key shows only prefix and last four`() {
        assertEquals("sk-…wxyz", Redactor.maskKey("sk-proj-0123456789abcdefwxyz"))
        assertEquals("AI…7890", Redactor.maskKey("AIzaSy1234567890"))
        assertEquals("••••", Redactor.maskKey("abc"))
    }
}
