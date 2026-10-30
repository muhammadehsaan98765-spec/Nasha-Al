# Nasha AI — Final Audit (Block 15)

Date: Original Block 15 source audit; updated 2026-10-30
Auditor: Original source review plus reconstruction verification
Original audit environment: text-only, no Android SDK, no Gradle, no device

The original statements below describe the Block 15 source audit. Current
build and packaging results are recorded in the final section of this file.

---

## 1. File inventory (Blocks 1–15)

### Gradle / Config
- settings.gradle.kts
- build.gradle.kts
- gradle.properties
- gradle/libs.versions.toml
- gradle/wrapper/gradle-wrapper.properties
- app/build.gradle.kts
- app/proguard-rules.pro
- local.properties.example
- .gitignore
- build.sh
- LICENSE
- README.md
- TERMUX-BUILD.md
- release_checklist.md
- **Missing in the original text delivery; generated in this reconstructed tree:** gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar

### Manifest + resources
- app/src/main/AndroidManifest.xml
- app/src/main/res/xml/accessibility_service_config.xml
- app/src/main/res/xml/backup_rules.xml
- app/src/main/res/xml/data_extraction_rules.xml
- app/src/main/res/values/colors.xml
- app/src/main/res/values/themes.xml
- app/src/main/res/values/strings.xml
- app/src/main/res/values-ur/strings.xml
- app/src/main/res/values-b+ur+Latn/strings.xml
- app/src/main/res/drawable/ic_launcher_foreground.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml

### Kotlin source (main)
- NashaApplication.kt
- MainActivity.kt
- AppContainer.kt (final = Block 13)
- accessibility/AccessibilityStatusFlow.kt
- accessibility/AccessibilityUtils.kt
- accessibility/NashaAccessibilityService.kt
- data/settings/SettingsRepository.kt
- data/settings/SecureKeyStore.kt
- data/settings/ShortcutRepository.kt
- data/network/NetworkMonitor.kt
- data/provider/AiProvider.kt
- data/provider/ProviderException.kt
- data/provider/ProviderConfigValidator.kt
- data/provider/GeminiProvider.kt
- data/provider/OpenAiCompatibleProvider.kt
- data/chat/ChatRepository.kt
- data/workspace/WorkspaceRepository.kt
- data/actions/ActionLogRepository.kt
- data/search/SearchProvider.kt
- domain/actions/ActionModels.kt
- domain/actions/CommandParser.kt
- domain/actions/ActionEngine.kt
- ui/theme/Theme.kt
- ui/components/StatusBanner.kt
- ui/NashaApp.kt (final = Block 10)
- ui/onboarding/OnboardingViewModel.kt
- ui/onboarding/OnboardingScreen.kt
- ui/chat/ChatViewModel.kt (final = Block 14)
- ui/chat/ChatScreen.kt (final = Block 14)
- ui/voice/VoiceViewModel.kt
- ui/voice/VoiceScreen.kt
- ui/actions/ActionsScreen.kt (final = Block 15)
- ui/actions/AppPickerDialog.kt (unchanged)
- ui/coding/CodingViewModel.kt
- ui/coding/CodingScreen.kt (final = Block 13)
- ui/history/HistoryViewModel.kt
- ui/history/HistoryScreen.kt (final = Block 13)
- ui/settings/SettingsViewModel.kt (final = Block 13)
- ui/settings/SettingsScreen.kt (final = Block 13)
- ui/about/AboutScreen.kt
- ui/about/Diagnostics.kt (final = Block 13)
- util/TtsManager.kt
- util/SafHelper.kt

### Kotlin tests (unit — app/src/test/)
- domain/actions/CommandParserTest.kt
- domain/actions/ActionRiskTest.kt
- domain/actions/ActionEngineTest.kt
- data/provider/GeminiProviderTest.kt
- data/provider/OpenAiCompatibleProviderTest.kt
- data/provider/ProviderConfigValidatorTest.kt
- data/settings/SecureKeyStoreTest.kt (FIXED in Block 15)
- data/search/WebSearchUrlBuilderTest.kt
- accessibility/AccessibilityDisabledTest.kt (FIXED in Block 15)

### Kotlin tests (instrumentation — app/src/androidTest/)
- ui/chat/ChatScreenTest.kt
- ui/UrduRtlTest.kt
- ui/onboarding/OnboardingTest.kt

---

## 2. Fixes applied in Block 15

### Fix A — SecureKeyStoreTest.kt
- **Problem:** imported `androidx.test.core.app.ApplicationProvider`,
  which is not declared in `testImplementation`. Robolectric does not pull
  `androidx.test:core` transitively. Compile error.
- **Fix:** replaced with `org.robolectric.RuntimeEnvironment.getApplication()`.

### Fix B — AccessibilityDisabledTest.kt
- **Same problem and same fix** as Fix A.

### Fix C — ActionsScreen.kt
- **Problem:** Block 14 defined a `clickableNoRipple` helper that called
  `androidx.compose.foundation.clickable(onClick = ...)` without a Modifier
  receiver. Compile error.
- **Fix:** removed the helper; used `Modifier.clickable(onClick = onTap)`
  directly in `ShortcutChip`. Added explicit import.

---

## 3. Audit — verified compatible (no change needed)

### 3.1 Gradle / version matrix
Gradle 8.9, AGP 8.7.2, Kotlin 2.0.21, Compose BOM 2024.10.01, JDK 17 — mutually compatible.

### 3.2 Manifest ↔ resources ↔ Kotlin packages
`.NashaApplication`, `.MainActivity`, `.accessibility.NashaAccessibilityService` all exist. All `@xml/*`, `@style/*`, `@string/*`, `@mipmap/*` referenced resources exist in Block 1.

### 3.3 SessionTurn ↔ ChatTurn
`SessionTurn.toChatTurn()` and `SessionTurn.from(ChatTurn)` exist in Block 4 ChatRepository. `ChatTurn` in Block 3. ChatViewModel imports both. Verified OK.

### 3.4 ChatUiState.canRetry wiring
Block 14 added `canRetry: Boolean = true`. ChatViewModel sets false on `rejectSuggestion()`. ChatScreen hides Retry when `canRetry == false`. Verified OK.

### 3.5 SettingsViewModel constructor
6 params (SettingsRepository, SecureKeyStore, ChatRepository, NetworkMonitor, SearchProvider, ShortcutRepository). SettingsRoute (Block 13) instantiates with all 6. Verified OK.

### 3.6 ActionsScreen installedShortcutPackages
ActionsRoute uses produceState to query PackageManager on IO. Passes Set<String> down. Verified OK.

### 3.7 NashaApp ↔ Routes
6 tabs wired: ChatRoute, VoiceRoute, ActionsRoute, CodingRoute, HistoryRoute, SettingsRoute. Verified OK.

### 3.8 MainActivity ↔ NashaApplication ↔ AppContainer
MainActivity reads container from application as NashaApplication. Verified OK.

### 3.9 ActionExecutor ↔ ActionEngine
Policy gate enforced. AndroidActionExecutor delegates accessibility to service, others to Intent. Verified OK.

### 3.10 TtsManager
Constructed on Main thread in AppContainer. Speak handles ur/ur-Latn/en. Verified by code review.

### 3.11 Provider layer
Gemini: x-goog-api-key header. OpenAI: Bearer header. ProviderConfigValidator rejects cleartext HTTP to non-localhost. Verified OK.

### 3.12 SafHelper integration
History/Coding/Diagnostics export all use SafHelper.writeTextToUri. Verified OK.

### 3.13 Security invariants
No hardcoded keys. API keys only in SecureKeyStore. usesCleartextTraffic=false. Accessibility service canRetrieveWindowContent=false. BLOCKED/UNKNOWN never execute. MEDIUM/HIGH require confirmation. AI suggestions allowlist + args validation. Verified by code review.

### 3.14 Diagnostics privacy
Report contains only: app version, Android version, network, provider, model, key-presence booleans, accessibility status, config valid. No keys, chat text, device identifiers. Verified OK.

---

## 4. Known limitations (documented, not blocking)

### 4.1 Robolectric + Android Keystore
SecureKeyStoreTest uses assumeTrue to skip (not fail) if Keystore probe fails. Real device/CI required.

### 4.2 AppCompatDelegate.setApplicationLocales on ComponentActivity
On API < 33, localized string switch may not propagate without AppCompat delegate. RTL and Compose strings still switch. Cosmetic.

### 4.3 ChatScreen uses R.string.next for "new conversation"
Placeholder label. Cosmetic.

### 4.4 OnboardingTest hardcoded English
onNodeWithText("Next"/"Skip"/"Back"/"Done"). Passes on English device.

### 4.5 Gradle wrapper binaries
User must run `gradle wrapper --gradle-version 8.9`.

### 4.6 AI Action Suggestions doubles token usage when ON
Feature OFF by default. Settings toggle.

### 4.7 TTS availability
TtsManager.isReady() returns false if no engine. ChatViewModel surfaces "Text-to-speech is not available on this device."

### 4.8 Voice recognition availability
VoiceViewModel emits Error if isRecognitionAvailable false.

### 4.9 Accessibility service runtime
Binding requires user enablement in Android Settings. No content reading, no auto-taps.

### 4.10 Shortcut persistence
ShortcutRepository validates package name and deduplicates. ActionsRoute queries PackageManager for installed state on IO. Uninstalled shortcuts removable via X button.

### 4.11 Provider error mapping
401 → Unauthorized, 403 → Forbidden, 429 → RateLimited, 5xx → Server, timeout → Timeout, IO → Network, JSON → Parse. Unit-tested but not executed.

### 4.12 Compose BOM API surface
HorizontalDivider, FlowRow, ExposedDropdownMenuBox, rememberLauncherForActivityResult, collectAsStateWithLifecycle all available in BOM 2024.10.01.

### 4.13 Kotlin 2.0.21 + Compose Compiler plugin
No kotlinCompilerExtensionVersion needed for Kotlin 2.0. Compose plugin alias present.

### 4.14 Tink 1.15.0 + AndroidKeystore
AndroidKeysetManager.Builder().withMasterKeyUri("android-keystore://...") supported. Keyset in SharedPreferences excluded from backup.

### 4.15 DataStore multi-file usage
4 separate DataStore files (settings/chats/action_log/shortcuts) with distinct names. Verified OK.

### 4.16 Coroutine scope hygiene
appScope uses SupervisorJob + Dispatchers.Default. ViewModels use viewModelScope.

### 4.17 Navigation state
rememberSaveable for tab index. pendingChatInput intentionally not saveable.

### 4.18 Onboarding flow
finish() guards with completed flag. NashaApp observes settings flow.

### 4.19 Strings — Urdu + Roman Urdu
Partial translations; missing keys fall back to English (Android default). No crash.

### 4.20 Icon and theme resources
minSdk=26 so mipmap-anydpi-v26 adaptive icons suffice. No PNG densities needed.

---

## 5. Original compilation readiness assessment (static only)

### 5.1 Blocking issues
**None identified by static review after the 3 Block 15 fixes.**

### 5.2 Possible warnings (not errors)
- Unused imports in some Compose files.
- Deprecated @Deprecated onError(String?) override in TtsManager.
- @Suppress("DEPRECATION") for getPackageInfo(String, Int) on API < 33.

### 5.3 Runtime readiness (device required)
- TTS engine availability and Urdu voice support.
- SpeechRecognizer availability.
- Accessibility service binding.
- DataStore across process death.
- Tink AndroidKeysetManager against Android Keystore (Robolectric may skip).
- SAF picker and file writes.
- Intent routing.

---

## 6. Items NOT available during the original Block 15 audit

| Item | Status | What to do |
|------|--------|-----------|
| Android SDK platform 35 | Not available | Install via sdkmanager |
| Android SDK build-tools 34+ | Not available | Same |
| Gradle 8.9 binary | Not available | `gradle wrapper --gradle-version 8.9` |
| gradlew / gradlew.bat / wrapper JAR | Not available (binary) | Generated by above |
| JDK 17 | Not available to this model | Must be on build host |
| Android emulator / device | Not available | Required for instrumented tests |
| Keystore for release signing | Not available | User must provide |
| Network access | Not available | Required for live provider tests |

**Nothing above is fabricated as present.**

---

## 7. Verification steps only possible on device / Android Studio

1. Open project in Android Studio → Sync Project with Gradle Files.
2. `./gradlew clean assembleDebug` — first true compile.
3. `./gradlew test` — first true unit test run.
4. `./gradlew lint` — first true lint run.
5. Install debug APK on device → manual QA per release_checklist.md.
6. Grant RECORD_AUDIO, enable accessibility, add real provider key, test end-to-end.
7. `./gradlew connectedDebugAndroidTest` — instrumented tests.
8. Release: create keystore, wire signingConfigs, `./gradlew assembleRelease`.

---

## 8. Original Block 15 checklist (historical)

- [x] Full audit of Blocks 1–14
- [x] Identified 3 compile-blocking issues
- [x] Fixed SecureKeyStoreTest (RuntimeEnvironment)
- [x] Fixed AccessibilityDisabledTest (RuntimeEnvironment)
- [x] Fixed ActionsScreen (Modifier.clickable usage)
- [x] Verified AppPickerDialog needs no change
- [x] Verified ChatUiState.canRetry wiring
- [x] Verified SettingsViewModel constructor
- [x] Verified ActionsScreen installedShortcutPackages
- [x] Verified NashaApp ↔ Routes
- [x] Verified MainActivity ↔ NashaApplication ↔ AppContainer
- [x] Verified ActionExecutor ↔ ActionEngine
- [x] Verified TtsManager integration
- [x] Verified provider layer
- [x] Verified SafHelper integration
- [x] Verified security invariants
- [x] Documented known limitations
- [ ] Build — NOT RUN
- [ ] Unit tests — NOT RUN
- [ ] Instrumentation tests — NOT RUN
- [ ] Lint — NOT RUN
- [ ] APK — NOT PRODUCED
- [ ] ZIP — NOT PRODUCED

## 9. Reconstruction and verification update (2026-10-30)

### Reconstructed source

- 82 text/configuration/documentation files are present.
- The three Gradle wrapper files were generated with Gradle 8.14.2 and
  verified to launch Gradle 8.9 under JDK 17.0.15.
- `ShortcutRepository.kt` was referenced by the supplied audit and app UI,
  but its source section was absent from the attachments. It was restored
  from the documented interface and behavior: validated package names,
  persisted shortcuts, and deduplication by package name.
- Package name remains `com.nashaai.assistant`; the `ActionType` enum still
  has 15 entries.

### Checks actually run

- `gradle wrapper --gradle-version 8.9` — exit 0; output included
  `BUILD SUCCESSFUL`.
- `./gradlew --version` — exit 0; reports Gradle 8.9 and JDK 17.0.15.
- `./gradlew help` — exit 0; `BUILD SUCCESSFUL`.
- `./gradlew clean assembleDebug` — exit 1 before Kotlin compilation:
  `SDK location not found. Define a valid SDK location with an ANDROID_HOME
  environment variable or by setting the sdk.dir path in your project's
  local properties file at '/home/runner/workspace/NashaAI/local.properties'.`
- `./gradlew test` — exit 1 for the same missing SDK location.
- `./gradlew lint` — exit 1 for the same missing SDK location.
- `NashaAI-source.zip` — created and verified; 85 files, including 82 text
  files and the three generated Gradle wrapper files. It excludes
  `local.properties`, build outputs, APKs, and signing keys.
- XML resources — 12 parsed successfully; 35 referenced string IDs were
  present in the default English resources.
- Instrumented tests and manual device QA — not run; no device or emulator.
- No APK was produced. No unit-test or lint results are available.

The Android SDK was not installed because acceptance of its license terms
was not authorized. A later build must start by providing an SDK location
and rerunning compilation, unit tests, and lint.
