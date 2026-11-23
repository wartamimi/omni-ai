package com.omniai.core.common

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertEquals

class EndpointValidatorTest {
    private val strict = EndpointValidator.Policy(allowLocalNetwork = false, allowCleartextLocal = false)
    private val local = EndpointValidator.Policy(allowLocalNetwork = true, allowCleartextLocal = true)

    @Test fun `accepts public https and trims trailing slash`() {
        val r = EndpointValidator.validateBaseUrl("https://api.openai.com/v1/", strict)
        assertIs<EndpointValidator.Result.Valid>(r)
        assertEquals("https://api.openai.com/v1", r.normalizedBaseUrl)
    }

    @Test fun `rejects cleartext public endpoints`() {
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("http://api.example.com/v1", local))
    }

    @Test fun `blocks private addresses unless allowed`() {
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("https://192.168.1.5:8080/v1", strict))
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("https://localhost/v1", strict))
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("https://10.0.0.1/v1", strict))
        assertIs<EndpointValidator.Result.Valid>(EndpointValidator.validateBaseUrl("http://192.168.1.5:11434/v1", local))
    }

    @Test fun `always blocks metadata endpoints and embedded credentials`() {
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("http://169.254.169.254/latest", local))
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("https://user:pass@api.example.com", strict))
        assertIs<EndpointValidator.Result.Invalid>(EndpointValidator.validateBaseUrl("ftp://example.com", strict))
    }

    @Test fun `join handles slashes and absolute paths`() {
        assertEquals("https://a.b/v1/chat/completions", EndpointValidator.join("https://a.b/v1/", "/chat/completions"))
        assertEquals("https://c.d/models", EndpointValidator.join("https://a.b/v1", "https://c.d/models"))
    }
}
