package com.locus.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private val CHECKBOX_LINE_REGEX = Regex("""^\s*[-*+]\s+\[([ xX])\]\s*(.*)$""")
private val BULLET_LINE_REGEX = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED_LINE_REGEX = Regex("""^\s*(\d+\.)\s+(.*)$""")
private val INLINE_TOKEN_REGEX = Regex("""(`[^`\n]+`|\*\*[^*\n]+\*\*|\*[^*\n]+\*)""")

/** Parses inline markdown spans: `code`, **bold**, and *italic*. */
fun parseInlineMarkdown(text: String): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val builder = AnnotatedString.Builder()
    var lastIndex = 0

    for (match in INLINE_TOKEN_REGEX.findAll(text)) {
        if (match.range.first > lastIndex) {
            builder.append(text.substring(lastIndex, match.range.first))
        }
        val token = match.value
        val startIdx = builder.length
        when {
            token.startsWith("`") && token.endsWith("`") -> {
                builder.append(token.removeSurrounding("`"))
                builder.addStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace),
                    startIdx,
                    builder.length,
                )
            }
            token.startsWith("**") && token.endsWith("**") -> {
                builder.append(token.removeSurrounding("**"))
                builder.addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    startIdx,
                    builder.length,
                )
            }
            token.startsWith("*") && token.endsWith("*") -> {
                builder.append(token.removeSurrounding("*"))
                builder.addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    startIdx,
                    builder.length,
                )
            }
        }
        lastIndex = match.range.last + 1
    }

    if (lastIndex < text.length) {
        builder.append(text.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}

@Composable
fun MarkdownPreview(
    body: String,
    onCheckboxToggle: (lineIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines = body.lines()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            MarkdownLineItem(
                line = line,
                lineIndex = index,
                onCheckboxToggle = onCheckboxToggle,
            )
        }
    }
}

@Composable
private fun MarkdownLineItem(
    line: String,
    lineIndex: Int,
    onCheckboxToggle: (lineIndex: Int) -> Unit,
) {
    when {
        line.isBlank() -> Spacer(modifier = Modifier.height(8.dp))
        line.startsWith("#") -> HeadingLine(line = line)
        CHECKBOX_LINE_REGEX.matches(line) -> {
            val match = CHECKBOX_LINE_REGEX.find(line)!!
            val isChecked = match.groupValues[1].equals("x", ignoreCase = true)
            val content = match.groupValues[2]
            CheckboxLine(
                isChecked = isChecked,
                text = content,
                onToggle = { onCheckboxToggle(lineIndex) },
            )
        }
        BULLET_LINE_REGEX.matches(line) -> {
            val content = BULLET_LINE_REGEX.find(line)!!.groupValues[1]
            BulletListLine(text = content)
        }
        NUMBERED_LINE_REGEX.matches(line) -> {
            val match = NUMBERED_LINE_REGEX.find(line)!!
            val prefix = match.groupValues[1]
            val content = match.groupValues[2]
            NumberedListLine(prefix = prefix, text = content)
        }
        line.startsWith("```") -> {
            FencedCodeMarker(marker = line)
        }
        else -> {
            Text(
                text = parseInlineMarkdown(line),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun HeadingLine(line: String) {
    val (style, text) =
        when {
            line.startsWith("###### ") ->
                MaterialTheme.typography.titleSmall to line.removePrefix("###### ")
            line.startsWith("##### ") ->
                MaterialTheme.typography.titleSmall to line.removePrefix("##### ")
            line.startsWith("#### ") ->
                MaterialTheme.typography.titleSmall to line.removePrefix("#### ")
            line.startsWith("### ") ->
                MaterialTheme.typography.titleMedium to line.removePrefix("### ")
            line.startsWith("## ") ->
                MaterialTheme.typography.titleLarge to line.removePrefix("## ")
            line.startsWith("# ") ->
                MaterialTheme.typography.headlineMedium to line.removePrefix("# ")
            else -> MaterialTheme.typography.titleMedium to line
        }

    Text(
        text = parseInlineMarkdown(text.trim()),
        style = style,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@Composable
private fun CheckboxLine(
    isChecked: Boolean,
    text: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (isChecked) TextDecoration.LineThrough else null,
            color =
                if (isChecked) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun BulletListLine(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "• ",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun NumberedListLine(
    prefix: String,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$prefix ",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = parseInlineMarkdown(text),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FencedCodeMarker(marker: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = marker,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
