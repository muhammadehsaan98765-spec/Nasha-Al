package com.nashaai.assistant.util

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Safe Storage Access Framework helpers.
 *
 * All operations take a URI returned by the system SAF picker
 * (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT). This is the only safe
 * way to write user-visible files on modern Android without broad storage
 * permissions.
 *
 * Design rules enforced here:
 *   - All I/O runs on Dispatchers.IO.
 *   - FileNotFound, SecurityException, and IOExceptions map to typed
 *     Result.failure; callers surface them as user-friendly strings.
 *   - Writes use mode "wt" (write + truncate) — SAF's "Create" picker
 *     already obtained explicit user consent for overwrite on that URI.
 *     We never open another app's file without going through the picker.
 *   - suggestedFileName() sanitizes characters that break common file
 *     systems and caps the name length.
 */
object SafHelper {

    /**
     * Writes [content] to [uri] using SAF.
     *
     * @return Result.success(Unit) on full write; Result.failure(e) otherwise.
     */
    suspend fun writeTextToUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: return@withContext Result.failure(
                    FileNotFoundException("Could not open output stream for URI")
                )
            stream.use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: FileNotFoundException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads UTF-8 text from [uri] using SAF. Useful for future import
     * flows; not consumed by the app yet.
     */
    suspend fun readTextFromUri(
        context: Context,
        uri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(
                    FileNotFoundException("Could not open input stream for URI")
                )
            val text = stream.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            Result.success(text)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: FileNotFoundException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Produces a SAF-recommended filename.
     *
     * @param baseName  Desired name (e.g., chat session title).
     * @param extension Extension without the dot (default "txt").
     */
    fun suggestedFileName(baseName: String, extension: String = "txt"): String {
        val cleaned = baseName
            .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]+"), "-")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-', '.', '_', ' ')
            .take(60)
            .ifBlank { "export" }
        return if (cleaned.lowercase().endsWith(".${extension.lowercase()}")) {
            cleaned
        } else {
            "$cleaned.$extension"
        }
    }

    /**
     * Maps SAF exceptions to safe user-facing strings. Never includes the
     * exception message (which may contain a URI or path).
     */
    fun userMessageFor(error: Throwable): String = when (error) {
        is SecurityException -> "Permission denied. Choose a different location."
        is FileNotFoundException -> "Could not open the selected file."
        is IOException -> "Write failed. Check free space and try again."
        else -> "Could not write the file."
    }
}
