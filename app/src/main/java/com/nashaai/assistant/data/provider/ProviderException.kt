package com.nashaai.assistant.data.provider

/**
 * Typed AI-provider failures.
 *
 * Every subtype carries a stable user-facing [message]. Callers should show
 * that string verbatim; internal [cause] is for diagnostics only and MUST NOT
 * include API keys or request headers.
 */
sealed class ProviderException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    /** HTTP 401, or provider-specific "invalid API key" response. */
    class Unauthorized(cause: Throwable? = null) : ProviderException(
        "API key invalid. Update it in Settings.", cause
    )

    /** HTTP 403 — key valid but lacks permission for this resource/model. */
    class Forbidden(cause: Throwable? = null) : ProviderException(
        "Access denied. Your key may lack permission for this model.", cause
    )

    /** HTTP 429 — rate limit or quota exceeded. */
    class RateLimited(cause: Throwable? = null) : ProviderException(
        "Rate limit reached. Wait a minute or upgrade your plan.", cause
    )

    /** Socket/read/connect timeout. */
    class Timeout(cause: Throwable? = null) : ProviderException(
        "Request timed out. Check your connection or increase timeout.", cause
    )

    /** Any other IOException (DNS, TLS, connection refused, etc.). */
    class Network(cause: Throwable? = null) : ProviderException(
        "No internet. Local features still work.", cause
    )

    /** HTTP 5xx or other unexpected status. Body is trimmed, never includes secrets. */
    class Server(
        val code: Int,
        val body: String,
        cause: Throwable? = null
    ) : ProviderException(
        "Provider error $code. Try a different model.", cause
    )

    /** Response could not be parsed, or was empty/unexpected. */
    class Parse(cause: Throwable? = null) : ProviderException(
        "Provider returned unexpected data.", cause
    )

    /** Local configuration problem (blank key, invalid URL, etc.). */
    class InvalidConfig(
        val reason: String,
        cause: Throwable? = null
    ) : ProviderException(
        "Invalid configuration: $reason", cause
    )
}
