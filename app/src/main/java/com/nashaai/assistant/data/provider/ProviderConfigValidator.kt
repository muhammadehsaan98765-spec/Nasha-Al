package com.nashaai.assistant.data.provider

import java.net.URI

/**
 * Result of validating a [ProviderConfig].
 */
sealed class ProviderValidationResult {
    object Valid : ProviderValidationResult()
    data class Invalid(val reason: String) : ProviderValidationResult()
}

/**
 * Enforces local safety rules before any network call is made.
 *
 * Rules:
 *   - API key must be non-blank.
 *   - Model name must be non-blank.
 *   - Base URL must be a parseable URI with an http(s) scheme.
 *   - Cleartext (http://) is only allowed for localhost / emulator loopback,
 *     never for arbitrary internet hosts — this prevents accidental leakage
 *     of API keys over the wire.
 *   - Timeout must be positive.
 */
object ProviderConfigValidator {

    private val LOCAL_HOSTS = setOf(
        "localhost",
        "127.0.0.1",
        "10.0.2.2",   // Android emulator alias for host machine
        "::1",
        "[::1]"
    )

    fun validate(config: ProviderConfig): ProviderValidationResult {
        val reason = reason(config)
        return if (reason == null) ProviderValidationResult.Valid
        else ProviderValidationResult.Invalid(reason)
    }

    /** Returns null if valid, otherwise a short human-readable reason. */
    fun reason(config: ProviderConfig): String? {
        if (config.apiKey.isBlank()) return "API key is missing"
        if (config.model.isBlank()) return "Model name is missing"
        if (config.timeoutSeconds <= 0) return "Timeout must be positive"

        val raw = config.baseUrl.trim()
        if (raw.isEmpty()) return "Base URL is missing"

        val uri: URI = try {
            URI(raw)
        } catch (_: Exception) {
            return "Base URL is not a valid URL"
        }

        val scheme = uri.scheme?.lowercase() ?: return "Base URL has no scheme"
        if (scheme != "https" && scheme != "http") {
            return "Base URL must use https:// or http://"
        }

        val host = uri.host?.lowercase() ?: return "Base URL has no host"

        if (scheme == "http" && host !in LOCAL_HOSTS) {
            return "Cleartext HTTP is only allowed for localhost/127.0.0.1/10.0.2.2 — use https://"
        }

        return null
    }
}
