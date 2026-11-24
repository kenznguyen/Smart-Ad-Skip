package com.kenznguyen.smartadskip.ai

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

data class ImportedQwenModel(
    val modelFile: File,
    val projectorFile: File
)

/**
 * Imports a ZIP model package into app-private storage. The active model directory is
 * replaced only after both GGUF files pass manifest size, SHA-256, and magic checks.
 */
class ModelPackageImporter(private val modelsRoot: File) {
    fun importPackage(packageStream: InputStream): ImportedQwenModel {
        require(modelsRoot.exists() || modelsRoot.mkdirs()) { "model_storage_unavailable" }
        val staging = File.createTempFile("qwen-import-", ".tmp", modelsRoot).apply {
            delete()
            check(mkdir()) { "model_staging_unavailable" }
        }
        val destination = File(modelsRoot, MODEL_DIRECTORY)
        val backup = File(modelsRoot, "$MODEL_DIRECTORY.previous")

        try {
            val seen = mutableSetOf<String>()
            ZipInputStream(packageStream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "directory_entries_not_allowed" }
                    require(entry.name in ALLOWED_ENTRIES) { "unexpected_package_entry" }
                    require(seen.add(entry.name)) { "duplicate_package_entry" }
                    val output = File(staging, entry.name)
                    output.outputStream().buffered().use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= entryLimit(entry.name)) { "package_entry_too_large" }
                            sink.write(buffer, 0, count)
                        }
                    }
                    zip.closeEntry()
                }
            }

            require(seen == ALLOWED_ENTRIES) { "package_missing_required_entries" }
            val manifest = Properties().apply {
                staging.resolve(MANIFEST_FILE).inputStream().buffered().use { load(it) }
            }
            validateManifest(manifest, staging)
            validateGguf(staging.resolve(MODEL_FILE))
            validateGguf(staging.resolve(PROJECTOR_FILE))

            if (backup.exists()) backup.deleteRecursively()
            if (destination.exists()) {
                check(destination.renameTo(backup)) { "cannot_backup_previous_model" }
            }
            if (!staging.renameTo(destination)) {
                if (backup.exists()) backup.renameTo(destination)
                error("cannot_activate_imported_model")
            }
            backup.deleteRecursively()
            return ImportedQwenModel(destination.resolve(MODEL_FILE), destination.resolve(PROJECTOR_FILE))
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
    }

    private fun validateManifest(properties: Properties, directory: File) {
        require(properties.getProperty("format") == MANIFEST_FORMAT) { "unsupported_model_package" }
        require(properties.getProperty("version") == "1") { "unsupported_manifest_version" }
        validateManifestFile(properties, directory, "model", MODEL_FILE, MAX_MODEL_BYTES)
        validateManifestFile(properties, directory, "mmproj", PROJECTOR_FILE, MAX_PROJECTOR_BYTES)
    }

    private fun validateManifestFile(
        properties: Properties,
        directory: File,
        prefix: String,
        expectedName: String,
        maximumBytes: Long
    ) {
        require(properties.getProperty("$prefix.file") == expectedName) { "invalid_manifest_filename" }
        val expectedSize = properties.getProperty("$prefix.size")?.toLongOrNull()
            ?: throw IllegalArgumentException("invalid_manifest_size")
        require(expectedSize in 4L..maximumBytes) { "invalid_manifest_size" }
        val file = directory.resolve(expectedName)
        require(file.isFile && file.length() == expectedSize) { "model_size_mismatch" }
        val expectedHash = properties.getProperty("$prefix.sha256").orEmpty().lowercase()
        require(expectedHash.matches(SHA256_PATTERN)) { "invalid_manifest_sha256" }
        require(sha256(file) == expectedHash) { "model_sha256_mismatch" }
    }

    private fun validateGguf(file: File) {
        val magic = ByteArray(4)
        file.inputStream().use { input ->
            require(input.read(magic) == magic.size) { "invalid_gguf_header" }
        }
        require(magic.contentEquals(GGUF_MAGIC)) { "invalid_gguf_header" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun entryLimit(name: String): Long = when (name) {
        MODEL_FILE -> MAX_MODEL_BYTES
        PROJECTOR_FILE -> MAX_PROJECTOR_BYTES
        MANIFEST_FILE -> MAX_MANIFEST_BYTES
        else -> 0L
    }

    companion object {
        const val MODEL_DIRECTORY = "qwen3-vl-2b"
        const val MANIFEST_FILE = "manifest.properties"
        const val MODEL_FILE = "model.gguf"
        const val PROJECTOR_FILE = "mmproj.gguf"
        const val MANIFEST_FORMAT = "smart-ad-skip-qwen3-vl"
        const val MAX_MODEL_BYTES = 8L * 1024 * 1024 * 1024
        const val MAX_PROJECTOR_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_MANIFEST_BYTES = 16L * 1024
        private val ALLOWED_ENTRIES = setOf(MANIFEST_FILE, MODEL_FILE, PROJECTOR_FILE)
        private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46)
    }
}
