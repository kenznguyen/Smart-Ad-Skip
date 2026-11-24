package com.kenznguyen.smartadskip.ai

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelPackageImporterTest {
    private val model = byteArrayOf(0x47, 0x47, 0x55, 0x46, 1, 2, 3)
    private val mmproj = byteArrayOf(0x47, 0x47, 0x55, 0x46, 4, 5, 6)

    @Test fun importsOnlyWhenBothGgufFilesMatchManifestHashes() {
        val root = createTempDir(prefix = "model-import-test")
        try {
            val result = ModelPackageImporter(root).importPackage(packageZip(model, mmproj))
            assertEquals(true, result.modelFile.isFile)
            assertEquals(true, result.projectorFile.isFile)
            assertEquals(sha256(model), sha256(result.modelFile.readBytes()))
            assertEquals(sha256(mmproj), sha256(result.projectorFile.readBytes()))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsHashMismatchWithoutActivatingPartialModel() {
        val root = createTempDir(prefix = "model-import-test")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ModelPackageImporter(root).importPackage(packageZip(model, mmproj, "0".repeat(64)))
            }
            assertEquals(false, File(root, ModelPackageImporter.MODEL_DIRECTORY).exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsNonGgufPayload() {
        val root = createTempDir(prefix = "model-import-test")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                ModelPackageImporter(root).importPackage(packageZip("NOPE".toByteArray(), mmproj))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun packageZip(modelBytes: ByteArray, projectorBytes: ByteArray, modelHashOverride: String? = null): ByteArray {
        val properties = Properties().apply {
            setProperty("format", ModelPackageImporter.MANIFEST_FORMAT)
            setProperty("version", "1")
            setProperty("model.file", "model.gguf")
            setProperty("model.size", modelBytes.size.toString())
            setProperty("model.sha256", modelHashOverride ?: sha256(modelBytes))
            setProperty("mmproj.file", "mmproj.gguf")
            setProperty("mmproj.size", projectorBytes.size.toString())
            setProperty("mmproj.sha256", sha256(projectorBytes))
        }
        val manifest = ByteArrayOutputStream().also { properties.store(it, null) }.toByteArray()
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                listOf("manifest.properties" to manifest, "model.gguf" to modelBytes, "mmproj.gguf" to projectorBytes)
                    .forEach { (name, bytes) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(bytes)
                        zip.closeEntry()
                    }
            }
        }.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
