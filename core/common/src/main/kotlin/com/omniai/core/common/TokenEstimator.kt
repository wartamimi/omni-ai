package com.omniai.core.common

/**
 * Local token estimate. Real tokenizers differ per model, so every value produced here is labelled
 * "estimated" in the UI. Heuristic: ~4 characters per token for Latin text, ~1 token per CJK character.
 */
object TokenEstimator {
    private const val PER_MESSAGE_OVERHEAD = 4

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (isCjk(ch)) cjk++ else other++
        }
        return cjk + ((other + 3) / 4)
    }

    fun estimateMessages(texts: List<String>): Int = texts.sumOf { estimate(it) + PER_MESSAGE_OVERHEAD }

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
    }
}
