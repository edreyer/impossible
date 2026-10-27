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
 *
 * README.md is checked the same way, but only for blocks marked with a `<!-- checked -->`
 * line just before the fence; their code lives in the `readme` test package.
 */
class TutorialSnippetsTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val sources = listOf(
        "src/commonTest/kotlin/io/liquidsoftware/safedomain/tutorial",
        "serialization/src/commonTest/kotlin/io/liquidsoftware/safedomain/serialization/tutorial",
        "testing/src/commonTest/kotlin/io/liquidsoftware/safedomain/testing/tutorial",
        "src/jvmTest/kotlin/io/liquidsoftware/safedomain/readme",
        "src/commonTest/kotlin/io/liquidsoftware/safedomain/recipes",
    )

    /** Recipes whose code is compiled; the others say in their first line that they are not. */
    private val compiledRecipes = listOf(File(root, "docs/recipes/normalize-then-parse.md"))

    private fun normalized(lines: List<String>): String =
        lines.joinToString("\n", prefix = "\n", postfix = "\n") { it.trim() }

    private fun snippets(markdown: String, markedOnly: Boolean = false): List<List<String>> {
        val blocks = mutableListOf<List<String>>()
        var current: MutableList<String>? = null
        var previous = ""
        for (line in markdown.lines()) {
            val marked = !markedOnly || previous.trim() == "<!-- checked -->"
            previous = line
            when {
                current == null && line.trim() == "```kotlin" && marked -> current = mutableListOf()
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
        val chapters = File(root, "docs/tutorial")
            .listFiles { file -> file.extension == "md" }
            .orEmpty()
            .sortedBy { it.name } + compiledRecipes
        assertTrue(chapters.size >= 12, "Expected the tutorial chapters in docs/tutorial")
        assertTrue(code.isNotEmpty(), "Expected the tutorial sources")

        val missing = chapters.flatMap { chapter ->
            snippets(chapter.readText())
                .filter { snippet -> code.none { normalized(snippet) in it } }
                .map { snippet -> "${chapter.name}: ${snippet.firstOrNull { it.isNotBlank() }?.trim()}" }
        }
        assertEquals(emptyList(), missing, "Snippets that no tutorial source contains")
    }

    @Test
    fun `every checked kotlin snippet in the README is compiled code`() {
        val code = sources
            .flatMap { File(root, it).walk().filter { file -> file.extension == "kt" }.toList() }
            .map { normalized(it.readLines()) }
        val checked = snippets(File(root, "README.md").readText(), markedOnly = true)
        assertTrue(checked.isNotEmpty(), "Expected checked snippets in README.md")
        val missing = checked
            .filter { snippet -> code.none { normalized(snippet) in it } }
            .map { snippet -> snippet.firstOrNull { it.isNotBlank() }?.trim() }
        assertEquals(emptyList(), missing, "README snippets that no source contains")
    }
}
