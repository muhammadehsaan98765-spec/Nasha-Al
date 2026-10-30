# Building Nasha AI in Termux

This document describes an honest attempt at building the project inside
Termux. It does **not** claim that Termux will succeed on every device. The
Android Gradle Plugin (AGP 8.7.2) and the Android SDK command-line tools
have specific host requirements that vary by Termux version, Android
version, and CPU architecture.

---

## 1. Reality check

Native Termux builds of Android projects frequently fail because:

- AGP 8.7.2 requires JDK 17 on the **host**. Termux's `openjdk-17` package
  works on some devices but not all.
- Android SDK `sdkmanager` uses native binaries (`aapt2`, `d8`, `aapt`)
  compiled for glibc, not Termux's bionic libc. The `aapt2` binary in
  particular is a common failure point.
- Disk space: a full Android SDK + Gradle cache + build outputs for this
  project typically consumes 4–8 GB.
- RAM: Gradle + Kotlin + Compose compiler routinely need 2–4 GB free.

**Do not assume Termux can build this project.** Test each step; stop and
use an alternative (see Section 5) if a step fails.

---

## 2. Required Termux packages

```
pkg update && pkg upgrade -y
pkg install -y openjdk-17 gradle git unzip wget
```

Check:

```
java -version      # must report 17.x
gradle --version   # any recent version; we use the wrapper anyway
```

If `java -version` reports anything below 17, stop and use Section 5.

---

## 3. Android SDK setup

1. Download the command-line tools from
   https://developer.android.com/studio#command-line-tools-only
2. Unzip into `$HOME/android-sdk/cmdline-tools/latest`.
3. Set environment variables (add to `~/.bashrc` or `~/.zshrc`):

   ```
   export ANDROID_HOME=$HOME/android-sdk
   export ANDROID_SDK_ROOT=$HOME/android-sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   ```

4. Accept licenses and install platform 35 + build-tools:

   ```
   sdkmanager --licenses
   sdkmanager "platform-tools" "platforms;android-35" "build-tools;34.0.0"
   ```

5. Point the project at the SDK. From the project root:

   ```
   cp local.properties.example local.properties
   echo "sdk.dir=$HOME/android-sdk" >> local.properties
   ```

**If `sdkmanager` or `aapt2` crashes** with "cannot execute binary file"
or "exec format error", that is the bionic/glibc mismatch. See Section 5.

---

## 4. Build attempt

From the project root:

```
# 1. Generate the Gradle wrapper (once)
gradle wrapper --gradle-version 8.9

# 2. Make it executable
chmod +x gradlew

# 3. Build
./gradlew clean assembleDebug test
```

If this succeeds, the debug APK is at:

```
app/build/outputs/apk/debug/app-debug.apk
```

To install on a connected device (requires `adb` from platform-tools):

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. When Termux fails

If any of the following occur, do not attempt to force a build in Termux:

- `sdkmanager` crashes with "exec format error" — this is a bionic vs.
  glibc problem. Android's `aapt2` binary requires glibc.
- `./gradlew` fails with "Could not find or load main class
  org.gradle.wrapper.GradleWrapperMain" — the wrapper JAR is missing
  (generate with `gradle wrapper` first).
- Out of memory during Kotlin compilation — reduce
  `org.gradle.jvmargs` in `gradle.properties` (already 2 GB; do not go
  below 1.5 GB or Compose compilation will OOM).
- "Failed to install the following Android SDK packages as some licences
  have not been accepted" — run `sdkmanager --licenses` and accept each.

**Alternatives that reliably work:**

1. **Android Studio on a desktop OS** (Linux, macOS, Windows). Supported
   path.
2. **A Linux VM** (Ubuntu 22.04/24.04, Debian 12). Install OpenJDK 17,
   Android SDK command-line tools, then run `./build.sh`.
3. **A CI workflow** (GitHub Actions, GitLab CI, Bitrise). Minimum steps:

   ```yaml
   - uses: actions/setup-java@v4
     with: { distribution: 'temurin', java-version: '17' }
   - uses: android-actions/setup-android@v3
     with: { packages: 'platforms;android-35 build-tools;34.0.0' }
   - run: gradle wrapper --gradle-version 8.9
   - run: chmod +x gradlew && ./gradlew clean assembleDebug test
   ```

4. **Proot-Distro / aarch64 Linux on Termux**: `proot-distro install
   ubuntu` then follow the Linux VM steps. Often works because the
   Ubuntu userspace provides glibc.

Do **not** fabricate build success. If the build has not been run and
verified, state that clearly.

---

## 6. Known issues and fixes

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| `exec format error` when `sdkmanager` runs | bionic vs. glibc | Use proot-distro Ubuntu or a Linux VM |
| `Could not find or load main class ... GradleWrapperMain` | wrapper JAR missing | `gradle wrapper --gradle-version 8.9` |
| `Unsupported class file major version 65` | JDK 17 not on PATH | `pkg install openjdk-17`, ensure first on PATH |
| `Dependency ... failed to resolve` | No network, or version typo | Check `gradle/libs.versions.toml` |
| `AAPT2 aapt2-*-linux.jar not found` | build-tools incomplete | `sdkmanager "build-tools;34.0.0"` |
| `OutOfMemoryError` in Kotlin compiler | Low RAM | Increase swap or move to a larger device/VM |
| `./gradlew: permission denied` | Not executable | `chmod +x gradlew` |
| Release APK missing | `assembleRelease` needs signing config | See Section 7 |

---

## 7. Release signing

`build.sh` (and `./gradlew assembleRelease`) produces an **unsigned** APK
by default. To sign:

1. Generate a keystore once (keep it safe; losing it means you can never
   update the app under the same package name):

   ```
   keytool -genkeypair -v -keystore nasha-release.jks \
     -alias nasha -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Create `keystore.properties` in the project root (do NOT commit it):

   ```
   storeFile=/absolute/path/to/nasha-release.jks
   storePassword=...
   keyAlias=nasha
   keyPassword=...
   ```

3. Wire a `signingConfigs` block in `app/build.gradle.kts` (Block 12 or
   later will provide this if requested). Until then, sign manually:

   ```
   apksigner sign --ks nasha-release.jks \
     --out app-release-signed.apk \
     app/build/outputs/apk/release/app-release-unsigned.apk
   ```

4. `zipalign -c -v 4 app-release-signed.apk` should pass.

---

## 8. Verifying what you actually built

- `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `./gradlew assembleRelease` → `app/build/outputs/apk/release/`
  (unsigned unless configured)
- `./gradlew test` → `app/build/reports/tests/test/index.html`
- `./gradlew connectedAndroidTest` → requires device or emulator

If a step did not run, its output directory will be empty. Do not assume
success from the presence of a directory — check the file exists and has a
non-zero size.

---

## 9. What to do if you cannot build in Termux

State clearly that Termux could not build this project on your device,
and use one of the alternatives in Section 5. It is better to say
"build not attempted on this host" than to fabricate a result.

The Nasha AI source tree is designed to be reproducible on any host with
JDK 17 + Android SDK platform 35 + Gradle 8.9. Those requirements are
not negotiable; AGP 8.7.2 will refuse to run without them.
