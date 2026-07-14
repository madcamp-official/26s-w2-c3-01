package com.melodybubble.server

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class MigrationVersionTest {
    @Test
    fun `migration versions are unique`() {
        val resource = requireNotNull(javaClass.classLoader.getResource("db/migration"))
        val versionPattern = Regex("^V([^_]+)__.+\\.sql$")
        val versions = Files.list(Paths.get(resource.toURI())).use { paths ->
            paths
                .map { it.fileName.toString() }
                .filter { it.endsWith(".sql") }
                .map { fileName ->
                    val match = requireNotNull(versionPattern.matchEntire(fileName)) {
                        "Invalid Flyway migration filename: $fileName"
                    }
                    match.groupValues[1] to fileName
                }
                .toList()
        }
        val duplicates = versions
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .filterValues { it.size > 1 }

        assertTrue(versions.isNotEmpty(), "No Flyway migrations were found")
        assertEquals(emptyMap<String, List<String>>(), duplicates)
    }
}
