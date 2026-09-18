package com.locus.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownPreviewLinkTest {
    @Test
    fun parseInlineMarkdown_wikilinkWithoutTitle_createsNoteIdAnnotation() {
        val input = "Check out [[note-uuid-1]] for details."
        val annotated = parseInlineMarkdown(input)

        assertEquals("Check out note-uuid-1 for details.", annotated.text)
        val annotations = annotated.getStringAnnotations("NOTE_ID", 0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("note-uuid-1", annotations.first().item)
    }

    @Test
    fun parseInlineMarkdown_wikilinkWithTitle_displaysTitleAndCreatesNoteIdAnnotation() {
        val input = "See [[note-uuid-2|Architecture Docs]] here."
        val annotated = parseInlineMarkdown(input)

        assertEquals("See Architecture Docs here.", annotated.text)
        val annotations = annotated.getStringAnnotations("NOTE_ID", 0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("note-uuid-2", annotations.first().item)
    }

    @Test
    fun parseInlineMarkdown_markdownLocusLink_createsNoteIdAnnotation() {
        val input = "Refer to [Architecture Guide](locus://note/note-uuid-3)."
        val annotated = parseInlineMarkdown(input)

        assertEquals("Refer to Architecture Guide.", annotated.text)
        val annotations = annotated.getStringAnnotations("NOTE_ID", 0, annotated.length)
        assertEquals(1, annotations.size)
        assertEquals("note-uuid-3", annotations.first().item)
    }

    @Test
    fun parseInlineMarkdown_sourcesSectionFormat_resolvesAllCitations() {
        val input =
            """
            ## Sources
            - [[note-1]] [Note One](locus://note/note-1)
            - [[note-2]] [Note Two](locus://note/note-2)
            """.trimIndent()
        val annotated = parseInlineMarkdown(input)

        val annotations = annotated.getStringAnnotations("NOTE_ID", 0, annotated.length)
        val noteIds = annotations.map { it.item }.toSet()
        assertTrue(noteIds.contains("note-1"))
        assertTrue(noteIds.contains("note-2"))
    }
}
