package com.nashaai.assistant.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.StandardCharsets

/**
 * Hardware-backed AES-256-GCM secret store for API keys.
 *
 * Architecture:
 *   - Master key: securely generated and stored by Android Keystore, never
 *     exported, tied to the app UID.
 *   - Tink AEAD keyset: the file that maps Tink key ids to actual key bytes,
 *     itself encrypted under the Android Keystore master key. Persisted in
 *     SharedPreferences file "nasha_aead_keyset" (excluded from backup in
 *     res/xml/backup_rules.xml).
 *   - Ciphertexts: per-alias blobs stored in SharedPreferences file
 *     "nasha_secure" (also excluded from backup). Associated data (AAD) is
 *     the alias string, so an attacker who swaps ciphertexts between aliases
 *     will cause AEAD verification to fail.
 *
 * Security invariants:
 *   - Never logs plaintext or ciphertext.
 *   - Never stores plaintext keys in DataStore, BuildConfig, files, or logs.
 *   - A rooted or compromised device can still recover a key; UI must
 *     disclose this honestly.
 *
 * Key rotation:
 *   AndroidKeysetManager.rotate() adds a new primary key while keeping the
 *   old key(s) in the keyset for decryption. We then re-encrypt every stored
 *   ciphertext under the new primary. If the process is killed between steps,
 *   old ciphertexts remain decryptable because the old key is still present.
 *   After successful rotation the old key(s) can be pruned with a future
 *   cleanup pass; for now they are retained so no value can be lost.
 */
class SecureKeyStore(private val context: Context) {

    companion object {
        private const val KEYSET_PREFS_FILE = "nasha_aead_keyset"
        private const val SECURE_PREFS_FILE = "nasha_secure"
        private const val KEYSET_NAME = "nasha_aead_keyset"
        private const val MASTER_KEY_URI = "android-keystore://nasha_master_key"
        private const val KEY_TEMPLATE = "AES256_GCM"

        // Well-known alias names used across the app.
        const val ALIAS_AI_API_KEY = "ai_api_key"
        const val ALIAS_BRAVE_SEARCH_KEY = "brave_search_api_key"

        @Volatile
        private var registered = false
    }

    init {
        if (!registered) {
            synchronized(SecureKeyStore::class.java) {
                if (!registered) {
                    // Idempotent; safe to call multiple times.
                    runCatching { AeadConfig.register() }
                    registered = true
                }
            }
        }
    }

    private val keysetManager: AndroidKeysetManager by lazy {
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_FILE)
            .withKeyTemplate(KeyTemplates.get(KEY_TEMPLATE))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
    }

    private val aead: Aead
        get() = keysetManager.keysetHandle.getPrimitive(Aead::class.java)

    private val securePrefs: SharedPreferences
        get() = context.getSharedPreferences(SECURE_PREFS_FILE, Context.MODE_PRIVATE)

    // ---- Public API ---------------------------------------------------

    /**
     * Encrypts [plaintext] under the current primary key, using [alias] as
     * AAD, and stores the base64 ciphertext.
     *
     * @return true on success, false if keystore/tink failed (caller should
     *         surface an error to the user).
     */
    fun save(alias: String, plaintext: String): Boolean {
        if (alias.isBlank()) return false
        return try {
            val aad = alias.toByteArray(StandardCharsets.UTF_8)
            val cipher = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), aad)
            val encoded = Base64.encodeToString(cipher, Base64.NO_WRAP)
            securePrefs.edit().putString(alias, encoded).commit()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Decrypts and returns the plaintext for [alias], or null if the alias
     * is absent, corrupted, or un-decryptable under the current keyset.
     */
    fun load(alias: String): String? {
        val encoded = securePrefs.getString(alias, null) ?: return null
        return try {
            val cipher = Base64.decode(encoded, Base64.NO_WRAP)
            val aad = alias.toByteArray(StandardCharsets.UTF_8)
            String(aead.decrypt(cipher, aad), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    /** Deletes the ciphertext for [alias]. */
    fun delete(alias: String): Boolean {
        if (alias.isBlank()) return false
        return try {
            securePrefs.edit().remove(alias).commit()
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true if any ciphertext is stored under [alias]. */
    fun hasKey(alias: String): Boolean = securePrefs.contains(alias)

    /** Aliases currently persisted. Useful for diagnostics/rotation. */
    fun aliases(): Set<String> = securePrefs.all.keys.toSet()

    /**
     * Generates a fresh AEAD primary key, rotates it in, and re-encrypts every
     * stored ciphertext under the new primary.
     *
     * Safe failure semantics:
     *   - We snapshot all plaintexts BEFORE rotation. If decryption of any
     *     existing ciphertext fails, its original ciphertext is preserved and
     *     the operation still proceeds for the rest.
     *   - After rotation the old key remains in the keyset, so if the process
     *     is killed midway, all previously stored ciphertexts remain readable.
     *
     * @return true on success, false if keyset rotation itself failed (the
     *         existing ciphertexts are then untouched).
     */
    fun rotateKeyset(): Boolean {
        val snapshot: Map<String, String?> = try {
            securePrefs.all.keys.associateWith { load(it) }
        } catch (_: Exception) {
            return false
        }

        try {
            keysetManager.rotate(KeyTemplates.get(KEY_TEMPLATE))
        } catch (_: Exception) {
            return false
        }

        // Re-encrypt every value that we successfully decrypted.
        snapshot.forEach { (alias, plaintext) ->
            if (plaintext != null) {
                save(alias, plaintext)
            }
        }
        return true
    }
}
