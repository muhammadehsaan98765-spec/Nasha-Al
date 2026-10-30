package com.nashaai.assistant

import android.app.Application

/**
 * Application entry point.
 *
 * Responsibilities:
 *   - Build [AppContainer] exactly once, before any Activity or Service.
 *   - Expose it as a non-null property so consumers never deal with
 *     nullable containers.
 *
 * Deliberately does NOT:
 *   - Make network calls at startup.
 *   - Read or write the encrypted key store at startup (SecureKeyStore is
 *     lazy on first access).
 *   - Touch any provider, search API, or accessibility state at startup.
 *
 * onCreate does a single, cheap object graph construction. No disk I/O
 * beyond what DataStore does lazily on first collection.
 */
class NashaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
