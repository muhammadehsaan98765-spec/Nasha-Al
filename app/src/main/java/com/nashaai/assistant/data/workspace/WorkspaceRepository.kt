package com.nashaai.assistant.data.workspace

import android.content.Context
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One saved code snippet in the workspace.
 * The actual code bytes live in `{id}.txt`; this record carries metadata.
 */
@Serializable
data class CodeEntry(
    val id: String,
    val name: String,
    val language: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * App-private code workspace.
 *
 * Layout:
 *   filesDir/code/
 *     index.json          — serialized List<CodeEntry>
 *     {id}.txt            — raw file content for the entry with that id
 *
 * Operations are serialized inside a Mutex-like flow via DataStore-free
 * file locking is overkill here; every public suspend function runs on
 * Dispatchers.IO and the index file is rewritten atomically (write-temp +
 * rename) to avoid torn reads on crash.
 */
class WorkspaceRepository(private val context: Context) {

    private val dir: File = File(context.filesDir, "code")
    private val indexFile: File = File(dir, "index.json")

    private companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    }

    @Serializable
    private data class IndexWrapper(
        val entries: List<CodeEntry> = emptyList()
    )

    // ---- Read ---------------------------------------------------------

    suspend fun list(): List<CodeEntry> = withContext(Dispatchers.IO) {
        ensureDir()
        readIndex().sortedByDescending { it.updatedAt }
    }

    suspend fun load(id: String): String? = withContext(Dispatchers.IO) {
        ensureDir()
        val entry = readIndex().firstOrNull { it.id == id } ?: return@withContext null
        val file = fileFor(entry.id)
        if (!file.exists()) return@withContext null
        try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun loadEntry(id: String): CodeEntry? = withContext(Dispatchers.IO) {
        readIndex().firstOrNull { it.id == id }
    }

    /**
     * Writes a file into the workspace and returns the resulting entry.
     * If [existingId] is provided, updates that entry in place; otherwise
     * creates a new one.
     */
    suspend fun save(
        name: String,
        language: String,
        code: String,
        existingId: String? = null
    ): Result<CodeEntry> = withContext(Dispatchers.IO) {
        try {
            ensureDir()
            val now = System.currentTimeMillis()
            val index = readIndex().toMutableList()

            val entry: CodeEntry = if (existingId != null) {
                val idx = index.indexOfFirst { it.id == existingId }
                if (idx >= 0) {
                    val updated = index[idx].copy(
                        name = name.trim().ifBlank { index[idx].name },
                        language = language.trim().ifBlank { index[idx].language },
                        updatedAt = now
                    )
                    index[idx] = updated
                    updated
                } else {
                    val created = CodeEntry(
                        id = UUID.randomUUID().toString(),
                        name = sanitizeName(name),
                        language = language,
                        createdAt = now,
                        updatedAt = now
                    )
                    index.add(created)
                    created
                }
            } else {
                val created = CodeEntry(
                    id = UUID.randomUUID().toString(),
                    name = sanitizeName(name),
                    language = language,
                    createdAt = now,
                    updatedAt = now
                )
                index.add(created)
                created
            }

            // Write content first, then index — so a crash leaves the
            // previous index consistent (the orphan file is harmless and
            // can be GC'd later if desired).
            fileFor(entry.id).writeText(code, Charsets.UTF_8)
            writeIndex(index)
            Result.success(entry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureDir()
            val index = readIndex().filterNot { it.id == id }
            writeIndex(index)
            fileFor(id).delete()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearAll(): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureDir()
            dir.listFiles()?.forEach { file ->
                if (file != indexFile) file.delete()
            }
            writeIndex(emptyList())
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the file for [id] if it exists, for SAF export.
     * Returns null if the entry doesn't exist.
     */
    suspend fun fileForExport(id: String): File? = withContext(Dispatchers.IO) {
        val file = fileFor(id)
        if (file.exists()) file else null
    }

    // ---- Internals ----------------------------------------------------

    private fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
    }

    private fun fileFor(id: String): File = File(dir, "$id.txt")

    private fun sanitizeName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) "untitled" else trimmed.take(80)
    }

    private fun readIndex(): List<CodeEntry> {
        if (!indexFile.exists()) return emptyList()
        return try {
            val raw = indexFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) emptyList()
            else json.decodeFromString(IndexWrapper.serializer(), raw).entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndex(list: List<CodeEntry>) {
        val payload = json.encodeToString(
            IndexWrapper.serializer(),
            IndexWrapper(list)
        )
        // Atomic-ish write: temp file then rename.
        val tmp = File(dir, "index.json.tmp")
        tmp.writeText(payload, Charsets.UTF_8)
        if (indexFile.exists()) indexFile.delete()
        tmp.renameTo(indexFile)
    }
}
