package com.nashaai.assistant.data.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderConfigValidatorTest {

    private fun okConfig(
        baseUrl: String = "https://api.example.com",
        model: String = "m", apiKey: String = "k", timeoutSeconds: Long = 30
    ) = ProviderConfig(baseUrl, model, apiKey, timeoutSeconds)

    @Test fun validHttpsConfig_isValid() {
        assertEquals(ProviderValidationResult.Valid,
            ProviderConfigValidator.validate(okConfig()))
    }

    @Test fun httpLocalhost_isValid() {
        assertEquals(ProviderValidationResult.Valid,
            ProviderConfigValidator.validate(okConfig(baseUrl = "http://localhost:8080/v1")))
    }

    @Test fun http127_0_0_1_isValid() {
        assertEquals(ProviderValidationResult.Valid,
            ProviderConfigValidator.validate(okConfig(baseUrl = "http://127.0.0.1:8080")))
    }

    @Test fun http10_0_2_2_emulatorAlias_isValid() {
        assertEquals(ProviderValidationResult.Valid,
            ProviderConfigValidator.validate(okConfig(baseUrl = "http://10.0.2.2:11434/v1")))
    }

    @Test fun cleartextPublicHost_isInvalid() {
        val r = ProviderConfigValidator.validate(okConfig(baseUrl = "http://example.com/v1"))
        assertTrue(r is ProviderValidationResult.Invalid)
        assertTrue((r as ProviderValidationResult.Invalid).reason.contains("Cleartext"))
    }

    @Test fun blankApiKey_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(apiKey = "   "))
            is ProviderValidationResult.Invalid)
    }

    @Test fun blankModel_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(model = ""))
            is ProviderValidationResult.Invalid)
    }

    @Test fun zeroTimeout_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(timeoutSeconds = 0))
            is ProviderValidationResult.Invalid)
    }

    @Test fun negativeTimeout_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(timeoutSeconds = -1))
            is ProviderValidationResult.Invalid)
    }

    @Test fun unsupportedScheme_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(baseUrl = "ftp://example.com"))
            is ProviderValidationResult.Invalid)
    }

    @Test fun malformedUrl_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(baseUrl = "not a url"))
            is ProviderValidationResult.Invalid)
    }

    @Test fun blankUrl_isInvalid() {
        assertTrue(ProviderConfigValidator.validate(okConfig(baseUrl = ""))
            is ProviderValidationResult.Invalid)
    }
}
