# Nasha AI — Release Checklist

Every item below is marked **PENDING** until the corresponding check has
actually been run on the target device / emulator and passed. Do not mark
anything PASSED based on code review alone.

Current verification note (2026-10-30): the Gradle 8.9 wrapper is present.
`./gradlew clean assembleDebug` was attempted and stopped before compilation
because the Android SDK location is missing. Unit tests and lint are also
blocked by that missing SDK. The SDK license was not accepted. All device
and release checks remain pending.

## 1. Build

- [ ] `./build.sh` completes: `./gradlew clean assembleDebug test` exits 0
- [ ] No compiler warnings that indicate API misuse
- [ ] `assembleRelease` succeeds
- [ ] Release APK is signed with a keystore you control (not debug key)
- [ ] `zipalign -c -v 4` passes on the release APK
- [ ] `apksigner verify` passes on the release APK
- [ ] ProGuard/R8 has not stripped Kotlinx Serialization serializers

## 2. Unit tests (Block 12)

- [ ] `./gradlew test` passes on JDK 17
- [ ] `CommandParserTest` — English, Roman Urdu, Urdu, unknown, slash
- [ ] `ActionRiskTest` — requiresConfirmation() for all ActionType
- [ ] `ActionEngineTest` — accessibility disabled path
- [ ] `GeminiProviderTest` — 200, 401, 403, 429, malformed JSON, header check
- [ ] `OpenAiCompatibleProviderTest` — 200, 401, malformed JSON
- [ ] `ProviderConfigValidatorTest` — https enforcement, blank model
- [ ] `SecureKeyStoreTest` — encrypt → decrypt → equal; delete → null
- [ ] `WebSearchUrlBuilderTest` — Uri.encode preserves special chars
- [ ] `AccessibilityDisabledTest` — goHome() returns Failure when no instance

## 3. Instrumentation tests (Block 12)

- [ ] `ChatScreenTest` — typing + send invokes callback
- [ ] `UrduRtlTest` — set LocalLayoutDirection.Rtl; assert right-aligned Text
- [ ] `OnboardingTest` — first launch shows onboarding; after skip, chat shows

## 4. Manual QA — first launch

- [ ] Onboarding appears once
- [ ] Language step: English / اردو / Roman Urdu selectable
- [ ] Skip works; Get Started works; onboarding does not reappear
- [ ] After onboarding, all 6 tabs render (Chat, Voice, Actions, Coding,
      History, Settings)

## 5. Manual QA — AI chat

- [ ] Without an API key: sending shows "No API key…" message, no crash
- [ ] With a Gemini key: chat returns a reply
- [ ] With an OpenAI-compatible key: chat returns a reply
- [ ] With an invalid key: shows "API key invalid. Update it in Settings."
- [ ] With a wrong base URL: shows a clear error, no crash
- [ ] With no network: shows "No internet. Local features still work."
- [ ] Retry after error re-sends successfully
- [ ] Clear chat removes on-screen messages
- [ ] New conversation starts a fresh session

## 6. Manual QA — voice

- [ ] First mic tap asks for RECORD_AUDIO
- [ ] Denying permission shows guidance, does not crash
- [ ] Granting permission allows listening
- [ ] Partial text streams while speaking
- [ ] Result transcript is editable
- [ ] "Send to chat" opens Chat with text prefilled
- [ ] Leaving the Voice tab stops recognition
- [ ] No speech captured without a mic tap

## 7. Manual QA — actions

- [ ] `/settings` opens Android Settings
- [ ] `/notifications` opens notification settings for Nasha AI
- [ ] `/open https://example.com` opens the browser (LOW risk, no confirm)
- [ ] `/open com.whatsapp` opens WhatsApp (if installed)
- [ ] `/search cats` opens the configured search engine
- [ ] `/copy hello` copies "hello" to the clipboard
- [ ] `/note Title | Body` creates a note in the workspace
- [ ] `/share hello` opens the share sheet (does not auto-send)
- [ ] `/message hello` opens the share sheet with warning about recipient
- [ ] `/read hello` triggers TTS (if engine installed)
- [ ] `/home`, `/back`, `/recents` FAIL with "accessibility_disabled" when
      the service is off
- [ ] After enabling accessibility, `/home`, `/back`, `/recents` succeed
- [ ] UNKNOWN command does not execute anything
- [ ] BLOCKED action is refused with "Blocked by safety policy"

## 8. Manual QA — coding workspace

- [ ] Prompt with no key shows "No API key…"
- [ ] Prompt with a key returns code
- [ ] Generated code appears in the monospace editor
- [ ] Copy button copies exactly the visible code
- [ ] Save writes a file; saved list updates
- [ ] Load opens a saved file
- [ ] Rename updates file name; delete removes it
- [ ] No auto-execution of any code
- [ ] No automatic export / SAF prompt

## 9. Manual QA — history

- [ ] With saveHistory ON, sessions appear after chats
- [ ] Tap a session opens the detail dialog
- [ ] Export opens SAF and writes the file
- [ ] Delete removes a single session
- [ ] Clear all removes everything after confirmation
- [ ] With saveHistory OFF, new chats are not saved

## 10. Manual QA — settings

- [ ] Change provider: dropdown updates, key must be re-saved
- [ ] Save key: shows "Key saved"; banner shows "Provider configured"
- [ ] Delete key: shows "Key deleted"; banner shows "No provider"
- [ ] Test connection with no key: shows "No API key saved"
- [ ] Test connection with valid key: shows "Connected. Reply: …"
- [ ] Test connection with invalid key: shows "API key invalid…"
- [ ] Change search engine: value persists across restarts
- [ ] Custom URL: saved and used
- [ ] Brave key: saved and delete works
- [ ] Result limit slider: value persists
- [ ] Timeout slider: value persists
- [ ] Safe search toggle: value persists
- [ ] Language chips: switching updates UI strings + layout direction
- [ ] Clear history button: shows confirmation, then clears
- [ ] Open Accessibility Settings button: opens Android Settings
- [ ] About + Diagnostics button: opens About screen
- [ ] Copy diagnostics: writes only sanitized text to clipboard
- [ ] No automatic connection test on Settings open
- [ ] No automatic network call on Settings open
- [ ] No automatic mic capture anywhere in Settings

## 11. API key security

- [ ] `grep -r "AIza" .` returns nothing (Google key pattern)
- [ ] `grep -r "sk-" .` returns nothing (OpenAI key pattern)
- [ ] `adb shell run-as com.nashaai.assistant cat shared_prefs/nasha_secure.xml`
      shows ciphertext only (base64 blob), never plaintext
- [ ] `adb shell run-as com.nashaai.assistant cat shared_prefs/nasha_aead_keyset.xml`
      shows a wrapped keyset (not the raw key)
- [ ] `adb logcat` never contains a key
- [ ] Delete key actually removes the entry from `nasha_secure.xml`

## 12. Permissions

- [ ] Manifest requests ONLY: INTERNET, RECORD_AUDIO, ACCESS_NETWORK_STATE
- [ ] No `QUERY_ALL_PACKAGES`
- [ ] `<queries>` block present
- [ ] Runtime permission dialog appears only on first mic tap
- [ ] Denying mic permission does not break other features

## 13. Accessibility disclosure

- [ ] About screen explains: what it does, what it cannot see, how to disable
- [ ] Onboarding accessibility step explains the same
- [ ] Settings → Accessibility section explains the same
- [ ] Service config declares `canRetrieveWindowContent="false"`
- [ ] Service does NOT read AccessibilityEvents

## 14. Privacy

- [ ] No `READ_CONTACTS`, `READ_SMS`, `READ_CALL_LOG`
- [ ] No automatic upload of any file
- [ ] No background microphone capture
- [ ] No silent data collection
- [ ] Diagnostics report has no keys, chat text, or device identifiers
- [ ] Share sheet opens; recipient is never auto-selected

## 15. RTL / localization

- [ ] Switch to اردو: UI right-aligns, RTL layout
- [ ] Switch to Roman Urdu: UI stays LTR
- [ ] Switch back to English: UI is LTR
- [ ] Long Urdu text wraps correctly in bubbles and cards
- [ ] Numbers and punctuation render correctly in Urdu

## 16. Offline / online behavior

- [ ] Airplane mode: local features work, network-required features
      show clear errors
- [ ] No fake AI responses when offline
- [ ] No fake search results when offline
- [ ] Status banner reflects the correct online/offline state

## 17. Export

- [ ] History export via SAF works
- [ ] No automatic export prompt on launch
- [ ] Export writes only the selected session (not everything)
- [ ] Canceling SAF does not crash

## 18. TTS

- [ ] `/read hello` speaks "hello" if a TTS engine is installed
- [ ] If no engine is installed, `/read` fails gracefully with clear message
- [ ] If the engine has no Urdu voice, `/read` falls back to English
- [ ] Speech stops when a new utterance starts
- [ ] Speech stops when the app leaves the foreground (via TtsManager.stop()
      if wired; otherwise verify manually)

## 19. Release signing

- [ ] Release build signed with your own keystore
- [ ] `keystore.properties` NOT committed to version control
- [ ] `local.properties` NOT committed to version control
- [ ] Signed APK installs on a clean device
- [ ] Signed APK launches without crashing

## 20. Final

- [ ] All above items PASSED or explicitly deferred with a documented reason
- [ ] README, TERMUX-BUILD.md, and this checklist reflect the actual state
- [ ] No claim of "compiled / tested / passed" without a real successful
      run behind it
