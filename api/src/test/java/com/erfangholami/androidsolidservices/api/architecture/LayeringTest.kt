package com.erfangholami.androidsolidservices.api.architecture

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Executable layering rules for the library.
 *
 * Each rule compares what the sources do against a **baseline** of the violations known when the
 * rule was written, as an equality — so the build fails both when a new violation appears and
 * when a baselined one is fixed without shrinking the baseline. Baselines only ever get smaller.
 */
class LayeringTest {

    @Test
    fun `one data module never imports another`() {
        val violations = apiSources()
            .mapNotNull { file ->
                val module = file.dataModuleOf() ?: return@mapNotNull null
                val foreign = DATA_MODULE_IMPORT.findAll(file.readText())
                    .map { it.groupValues[1] }
                    .filter { it.first().isLowerCase() }
                    .filter { it != module && it !in SHARED_DATA_MODULE_PACKAGES }
                    .toSortedSet()
                if (foreign.isEmpty()) null else "${file.relativePath()} -> ${foreign.joinToString()}"
            }
            .toSortedSet()

        assertEquals(
            "data modules are siblings: share code through datamodule/core or Shared, never " +
                "by importing each other (plan §4.3).",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `data modules do not depend on the sharing engine`() {
        val violations = apiSources()
            .filter { it.dataModuleOf() != null }
            .filter { it.readText().contains("import $API.sharing.") }
            .map { it.relativePath() }
            .toSortedSet()

        assertEquals(
            "a data module must not reach into sharing; sharing depends on data modules, not " +
                "the other way round (plan §4.2).",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `library code carries no comments except KDoc`() {
        val violations = (apiSources() + sharedSources() + clientSources())
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trimStart()
                    val isComment = trimmed.startsWith("//") ||
                        (trimmed.startsWith("/*") && !trimmed.startsWith("/**"))
                    if (isComment) "${file.relativePath()}:${index + 1}" else null
                }
            }
            .toSortedSet()

        assertEquals(
            "library code explains itself through KDoc on public declarations; anything a " +
                "comment would say belongs in a test name or a feature doc.",
            emptySet<String>(),
            violations,
        )
    }

    private fun apiSources(): List<File> = sourcesUnder(API_ROOT)

    private fun sharedSources(): List<File> = sourcesUnder(SHARED_ROOT)

    private fun clientSources(): List<File> = sourcesUnder(CLIENT_ROOT)

    private fun sourcesUnder(root: File): List<File> =
        if (!root.isDirectory) emptyList()
        else root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.dataModuleOf(): String? =
        DATA_MODULE_PATH.find(relativeTo(REPO).path.replace(File.separatorChar, '/'))
            ?.groupValues?.get(1)

    private fun File.relativePath(): String =
        relativeTo(REPO).path.replace(File.separatorChar, '/')
            .removePrefix("api/src/main/java/com/erfangholami/androidsolidservices/")
            .removePrefix("Shared/src/main/java/com/erfangholami/androidsolidservices/")
            .removePrefix("client/src/main/java/com/erfangholami/androidsolidservices/")

    private companion object {
        const val API = "com.erfangholami.androidsolidservices.api"

        val SHARED_DATA_MODULE_PACKAGES = setOf("typeindex", "core")

        val DATA_MODULE_IMPORT = Regex("""import $API\.datamodule\.([a-zA-Z0-9_]+)[.\s]""")
        val DATA_MODULE_PATH = Regex("""api/datamodule/([a-zA-Z0-9_]+)/""")

        val REPO: File = generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile && File(it, "api").isDirectory }

        val API_ROOT = File(REPO, "api/src/main/java")
        val SHARED_ROOT = File(REPO, "Shared/src/main/java")
        val CLIENT_ROOT = File(REPO, "client/src/main/java")
    }
}
