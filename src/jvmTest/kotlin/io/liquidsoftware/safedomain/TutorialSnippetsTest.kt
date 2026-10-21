package io.liquidsoftware.safedomain

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps docs/tutorial honest: every `kotlin` code block in a chapter must appear, line for
 * line, in the tutorial test sources, which compile and run on every target. Indentation
 * is ignored, so a snippet may be lifted out of a class body; anything else that differs
 * fails here with the chapter and the snippet's first line.
 */
class TutorialSnippetsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val sources = listOf(
        "src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial",
        "serialization/src/commonTest/kotlin/io/liquidsoftware/safedomain/serialization/tutorial",
        "testing/src/commonTest/kotlin/io/liquidsoftware/safedomain/testing/tutorial",
    )

    private fun normalized(lines: List<String>): String = lines.joinToString("\n", prefix = "\n", postfix = "\n") { it.trim() }

    private fun snippets(markdown: String): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        for (line in markdown.lines()) {
            when {
                current == null && line.trim() == "```kotlin" -> current = mutableListOf()
                current != null && line.trim() == "```" -> {
                    blocks += current
                    current = null
                }
                current != null -> current += line
            }
        }
        return blocks
    }

    @Test
    fun `every kotlin snippet in the tutorial is compiled tutorial code`() {
        val code = sources
            .flatMap { File(root, it).walk().filter { file -> file.extension == "kt" }.toList() }
            .map { normalized(it.readLines()) }
        val chapters = File(root, "docs/tutorial").listFiles { file -> file.extension == "md" }.orEmpty().sortedBy { it.name }
        assertTrue(chapters.size >= 12, "Expected the tutorial chapters in docs/tutorial")
        assertTrue(code.isNotEmpty(), "Expected the tutorial sources")

        val missing = chapters.flatMap { chapter ->
            snippets(chapter.readText())
                .filter { snippet -> code.none { normalized(snippet) in it } }
                .map { snippet -> "${chapter.name}: ${snippet.firstOrNull { it.isNotBlank() }?.trim()}" }
        }
        assertEquals(emptyList(), missing, "Snippets that no tutorial source contains")
    }
}
