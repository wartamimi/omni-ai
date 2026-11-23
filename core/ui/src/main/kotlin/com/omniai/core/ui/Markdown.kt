package com.omniai.core.ui

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown

/**
 * Renders model output as Markdown (headings, lists, tables, links, code). Code highlighting can be
 * turned off in settings. LaTeX is shown as source text: there is no native math renderer bundled.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    renderMarkdown: Boolean = true,
    highlightCode: Boolean = true,
) {
    if (!renderMarkdown) {
        SelectionContainer(modifier) { Text(text, style = MaterialTheme.typography.bodyLarge) }
        return
    }
    if (highlightCode) {
        Markdown(
            text,
            modifier = modifier,
            components = markdownComponents(codeBlock = highlightedCodeBlock, codeFence = highlightedCodeFence),
        )
    } else {
        Markdown(text, modifier = modifier)
    }
}
