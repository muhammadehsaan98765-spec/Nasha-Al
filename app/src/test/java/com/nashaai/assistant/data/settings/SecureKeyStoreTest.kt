package com.nashaai.assistant.data.settings

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * SecureKeyStore tests under Robolectric.
 *
 * BLOCK 15 FIX:
 *   The previous version used `androidx.test.core.app.ApplicationProvider`.
 *   That class is provided by `androidx.test:core`, which is NOT declared
 *   in `testImplementation` (Block 1 only declares it under
 *   androidTestImplementation via `androidx.test.ext:junit`). Robolectric
 *   does NOT pull it in transitively. This caused a compile error.
 *
 *   Fixed by using `org.robolectric.RuntimeEnvironment.getApplication()`,
 *   which is always available when Robolectric is on the test classpath.
 *
 * LIMITATION (unchanged):
 *   Tink's AndroidKeysetManager relies on a functional Android Keystore.
 *   Robolectric's Keystore shadow may be incomplete. We probe once in
 *   @Before; if the probe fails we skip each test with a clear reason
 *   instead of failing.
 *
 * SECURITY: NEVER uses real API keys — only "test-..." placeholders.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureKeyStoreTest {

    private lateinit var store: SecureKeyStore
    private var keystoreAvailable = false
    private var probeError: String? = null

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        store = SecureKeyStore(ctx)
        keystoreAvailable = try {
            val probeOk = store.save("__nasha_probe__", "probe")
            if (probeOk) {
                store.delete("__nasha_probe__")
                true
            } else {
                probeError = "save() returned false (Keystore likely unavailable)"
                false
            }
        } catch (e: Throwable) {
            probeError = "${e.javaClass.simpleName}: ${e.message}"
            false
        }
    }

    private fun requireKeystore() {
        assumeTrue(
            "Android Keystore unavailable in this test env: $probeError",
            keystoreAvailable
        )
    }

    @Test
    fun saveThenLoad_returnsSameValue() {
        requireKeystore()
        val ok = store.save("test_alias", "test-value-123")
        assertTrue(ok)
        assertEquals("test-value-123", store.load("test_alias"))
    }

    @Test
    fun hasKey_returnsFalseBeforeSave_andTrueAfterSave() {
        requireKeystore()
        assertFalse(store.hasKey("test_hk_alias"))
        store.save("test_hk_alias", "v")
        assertTrue(store.hasKey("test_hk_alias"))
    }

    @Test
    fun delete_removesValue() {
        requireKeystore()
        store.save("test_del_alias", "v")
        assertTrue(store.delete("test_del_alias"))
        assertNull(store.load("test_del_alias"))
        assertFalse(store.hasKey("test_del_alias"))
    }

    @Test
    fun load_unknownAlias_returnsNull() {
        requireKeystore()
        assertNull(store.load("never_saved_alias"))
    }

    @Test
    fun aliasIsolation_independentValues() {
        requireKeystore()
        store.save("alias_a", "value-a")
        store.save("alias_b", "value-b")
        assertEquals("value-a", store.load("alias_a"))
        assertEquals("value-b", store.load("alias_b"))

        store.delete("alias_a")
        assertNull(store.load("alias_a"))
        assertEquals("value-b", store.load("alias_b"))
    }

    @Test
    fun aliases_reflectsStoredKeys() {
        requireKeystore()
        store.save("aliases_test_1", "v1")
        store.save("aliases_test_2", "v2")
        val keys = store.aliases()
        assertTrue(keys.contains("aliases_test_1"))
        assertTrue(keys.contains("aliases_test_2"))
    }

    @Test
    fun save_emptyAlias_returnsFalse() {
        requireKeystore()
        assertFalse(store.save("", "value"))
    }

    @Test
    fun rotateKeyset_succeeds_andPreservesValues() {
        requireKeystore()
        store.save("rotate_test_a", "value-a")
        store.save("rotate_test_b", "value-b")
        val rotated = store.rotateKeyset()
        assertTrue(rotated)
        assertEquals("value-a", store.load("rotate_test_a"))
        assertEquals("value-b", store.load("rotate_test_b"))
    }
}
