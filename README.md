# Nasha AI

A multilingual, voice-first Android personal assistant. Chat with an AI
provider you configure, run a small allowlist of validated device actions,
and optionally use Android's accessibility service for Home / Back / Recents
navigation only. Nasha AI never bypasses the lock screen, never captures
passwords, and never sends messages on your behalf.

## Project status

The supplied source blocks and final documentation have been reconstructed.
As of 2026-10-30, the Gradle 8.9 wrapper has been generated with JDK 17.
`./gradlew clean assembleDebug` was attempted but stopped before compilation
because no Android SDK location is configured. The Android SDK was not
installed because its license acceptance was not authorized. Unit tests,
lint, device tests, and APK packaging have not been verified. Do not treat
this source as build-verified until those checks complete. A source archive
(`NashaAI-source.zip`) is supplied; creating it does not imply a successful
Android build.

---

## What it does

- **Chat** with an AI provider you choose: Gemini (`generativelanguage.googleapis.com`)
  or any OpenAI-compatible endpoint (OpenAI, OpenRouter, Groq, or a local
  Ollama server at `http://10.0.2.2:11434/v1` on the Android emulator).
- **Voice input** in English, Urdu, and Roman Urdu using Android's
  SpeechRecognizer. Recognised speech lands in the Chat input; you decide
  whether to send it.
- **Deterministic actions** parsed from typed commands (`/open`, `/search`,
  `/copy`, `/note`, `/message`, `/read`, `/settings`, `/home`, `/back`,
  `/recents`, `/save-code`) or recognised short natural-language phrases.
  Every action previews what it will do and asks for confirmation on
  MEDIUM / HIGH risk.
- **Device actions**: open an installed app, open Android Settings, open
  notification settings, perform Home / Back / Recents when accessibility
  is enabled, copy text, save a note, share text (never sends — the system
  share sheet opens and you pick the recipient), read a response aloud via
  the device's TextToSpeech engine.
- **Coding workspace**: prompt → AI-generated code → edit → copy → save
  locally (`filesDir/code/`) → export via SAF. Code is **never** compiled
  or executed by Nasha AI.
- **Chat history**: multiple sessions, list / open / export / delete /
  clear all. Persisted locally in DataStore when you enable "Save chat
  history".
- **Multi-language UI**: English, Urdu (RTL), Roman Urdu (LTR), with
  proper RTL layout direction on the root composable.
- **Accessibility is optional**. It is used only for Home / Back / Recents.
  The service declares `canRetrieveWindowContent="false"` so it cannot
  read screen text, and the code ignores all accessibility events.

## What it explicitly does NOT do

- Does not bypass the lock screen, PIN, password, or biometrics.
- Does not read screen content, messages, or text fields from other apps.
- Does not capture background microphone audio. The mic opens only on the
  Voice screen after you tap the mic button, and the service is not
  claimed to work with the screen off.
- Does not send messages on your behalf. `PREPARE_MESSAGE` and
  `SHARE_TEXT` open the system share sheet; you choose the recipient and
  press Send in the target app.
- Does not execute raw AI output as commands. AI replies are text only.
- Does not auto-collect contacts, SMS, call logs, files, or device content.
- Does not run arbitrary generated code. Coding workspace saves text only.
- Does not store API keys in plaintext, DataStore, logs, BuildConfig, or
  backups. Keys are encrypted with Tink under an Android Keystore master
  key.

## Requirements

- **Android Studio** (latest stable) for GUI development, OR
  **Termux / Linux** command line for headless builds (see `TERMUX-BUILD.md`).
- **JDK 17** — required by Android Gradle Plugin 8.7.2.
- **Gradle wrapper 8.9** — `gradlew`, `gradlew.bat`, and the wrapper JAR
  are included in this source tree.
- **Android SDK** with **platform 35** and **build-tools 34+** installed.
- **Android device or emulator** with API 26 or higher.

## Project structure

```
NashaAI/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── local.properties.example
├── .gitignore
├── LICENSE
├── README.md
├── build.sh
├── release_checklist.md
├── TERMUX-BUILD.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/nashaai/assistant/
        │   │   ├── NashaApplication.kt
        │   │   ├── MainActivity.kt
        │   │   ├── AppContainer.kt
        │   │   ├── accessibility/
        │   │   ├── data/
        │   │   │   ├── settings/   (SettingsRepository, SecureKeyStore)
        │   │   │   ├── chat/       (ChatRepository, ChatSession)
        │   │   │   ├── provider/   (AiProvider, Gemini, OpenAI-compatible,
        │   │   │   │                ProviderException, Validator)
        │   │   │   ├── search/     (SearchProvider)
        │   │   │   ├── workspace/  (WorkspaceRepository)
        │   │   │   ├── actions/    (ActionLogRepository)
        │   │   │   └── network/    (NetworkMonitor)
        │   │   ├── domain/
        │   │   │   └── actions/    (ActionModels, CommandParser, ActionEngine)
        │   │   ├── ui/
        │   │   │   ├── NashaApp.kt
        │   │   │   ├── theme/Theme.kt
        │   │   │   ├── components/StatusBanner.kt
        │   │   │   ├── onboarding/
        │   │   │   ├── chat/
        │   │   │   ├── voice/
        │   │   │   ├── actions/
        │   │   │   ├── coding/
        │   │   │   ├── history/
        │   │   │   ├── settings/
        │   │   │   └── about/     (AboutScreen, Diagnostics)
        │   │   └── util/          (TtsManager, SafHelper)
        │   └── res/               (strings EN/UR/Roman, themes, icons)
        ├── test/java/com/nashaai/assistant/    (unit tests included)
        └── androidTest/java/com/nashaai/assistant/  (device tests included)
```

## Setup

1. Copy `local.properties.example` to `local.properties` and set
   `sdk.dir=/path/to/Android/Sdk`.
2. Open in Android Studio → **Sync Project with Gradle Files**, or run
   `./gradlew assembleDebug` from a terminal (after generating the wrapper,
   see Requirements).
3. On first launch, the app shows a 3-step onboarding: language, provider
   guidance (no API key entry required here — you add it later in
   Settings), privacy/accessibility explanation.
4. Open **Settings** → **AI Provider** → pick Gemini or OpenAI-compatible
   → paste your key → tap **Save key** → tap **Test connection**. If the
   test fails, check the base URL, model name, and network.
5. Grant `RECORD_AUDIO` when you first tap the mic on the Voice tab.
6. Enable accessibility only if you want Home / Back / Recents. Nasha AI
   does not enable it for you; you do it in Android Settings.

## Provider configuration

| Provider | Base URL default | Model default | Notes |
|----------|-----------------|---------------|-------|
| Gemini | `https://generativelanguage.googleapis.com` | `gemini-2.0-flash` | Key from https://aistudio.google.com/app/apikey |
| OpenAI-compatible | `https://api.openai.com/v1` | `gpt-4o-mini` | Billing separate from ChatGPT subscription. Compatible with OpenRouter, Groq, local Ollama |

The app only sends requests to the base URL you configure. Cleartext
`http://` is allowed only for `localhost`, `127.0.0.1`, and `10.0.2.2`
(Android emulator loopback). Every other scheme/endpoint must use
`https://`.

**Switching providers requires re-saving the API key.** There is a single
encrypted key slot (`SecureKeyStore.ALIAS_AI_API_KEY`). Adding per-provider
slots is a planned enhancement.

## Permissions

| Permission | Why |
|-----------|-----|
| `INTERNET` | AI provider, Brave Search API, browser intents |
| `RECORD_AUDIO` | Voice input. Requested on first mic tap, never at launch |
| `ACCESS_NETWORK_STATE` | Network status banner |

No `READ_CONTACTS`, `READ_SMS`, `READ_CALL_LOG`, or `QUERY_ALL_PACKAGES`.
Package visibility uses a narrow `<queries>` block for launchable apps.

## Offline vs online

- **Works offline**: chat history, saved notes, coding workspace,
  settings, device actions (open app, open settings, copy text, save
  note), accessibility navigation, TTS if your engine has offline voices.
- **Requires network**: AI chat responses, Brave Search API, opening an
  `http(s)` URL (unless the browser caches it).

## Building

If the Gradle wrapper files exist:

```
./build.sh
```

If they do not (fresh source drop), generate them first:

```
gradle wrapper --gradle-version 8.9
chmod +x gradlew
./build.sh
```

`build.sh` runs `./gradlew clean assembleDebug test` and zips the source
tree to `NashaAI-source.zip` (excludes `build/`, `.gradle/`,
`local.properties`, `*.iml`, `.idea/`). It does not sign anything and does
not download wrapper binaries.

See `TERMUX-BUILD.md` for Termux-specific caveats.

## Release checklist

See `release_checklist.md`. Every item is marked PENDING until the
corresponding build/test actually runs and succeeds on the target device.

## License

Apache License 2.0 — see `LICENSE`.

## Security disclosure

API keys are encrypted with Tink AES-256-GCM under an Android Keystore
master key. This protects keys from casual extraction (backup, log,
`adb shell run-as`) but **cannot** protect against a rooted or otherwise
compromised device. If you need stronger isolation, run your own backend
proxy and keep the provider key off the device entirely.

---

## اردو خلاصہ (Urdu summary)

**نشا اے آئی** ایک کثیراللسانی، آواز پر مبنی اینڈرائیڈ اسسٹنٹ ہے۔ یہ
آپ کے منتخب کردہ AI پرووائیڈر (Gemini یا کسی بھی OpenAI-compatible
سروس) کے ساتھ چیٹ کرتا ہے، صرف چند محدود اور محفوظ ڈیوائس ایکشنز
چلاتا ہے، اور اختیاری طور پر صرف Home/Back/Recents کے لیے Android
Accessibility استعمال کرتا ہے۔

**اہم نکات:**

- یہ **لاک اسکرین، PIN یا پاس ورڈ کو کبھی bypass نہیں کرتا**۔
- **اسکرین کا مواد نہیں پڑھتا** — accessibility سروس میں
  `canRetrieveWindowContent=false` سیٹ ہے۔
- **خود بخود پیغام نہیں بھیجتا** — share sheet کھلتا ہے، recipient
  آپ خود منتخب کرتے ہیں۔
- **AI کے جواب کو کبھی خودکار کمانڈ نہیں سمجھتا**۔
- **API keys** کو Android Keystore کے ذریعے encrypt کر کے محفوظ رکھتا
  ہے، نہ logs میں، نہ backup میں۔

**زیرِ تعمیر:** بلاک 1 تا 11 مکمل ہو چکے ہیں۔ **کمپائل یا ٹیسٹ نہیں
کیا گیا**۔ باقی بلاکس 12–14 میں ٹیسٹس، پالش اور حتمی جانچ شامل ہیں۔

**درکار:** JDK 17، Gradle wrapper 8.9، Android SDK platform 35، API 26+
ڈیوائس یا ایمولیٹر۔

**Build کرنے کے لیے:**

```
gradle wrapper --gradle-version 8.9
chmod +x gradlew
./build.sh
```

تفصیلی ہدایات کے لیے `TERMUX-BUILD.md` اور `README.md` دیکھیں۔

اپنے API key کے حصول کے لیے:

- Gemini: https://aistudio.google.com/app/apikey
- OpenAI-compatible: https://platform.openai.com/api-keys

**نوٹ:** یہ کوڈ ابھی تک کمپائل یا ٹیسٹ نہیں ہوا — براہِ کرم اپنی
ڈیوائس پر بلڈ کر کے تصدیق کریں۔
