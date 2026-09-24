package com.dicereligion.rateprince.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The domain layer must stay plain Kotlin/JVM so it is testable without a device. */
class DomainPurityTest {

    @Test
    fun `domain has no Android imports`() {
        // Unit tests run with the module directory as the working directory.
        val domainDir = File("src/main/java/com/dicereligion/rateprince/domain")
        assertTrue("domain dir not found at ${domainDir.absolutePath}", domainDir.isDirectory)

        val offenders = domainDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .filter { it.startsWith("import android.") || it.startsWith("import androidx.") }
                    .map { "${file.name}: $it" }
            }
            .toList()

        assertTrue("Android imports in domain/:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
