#!/usr/bin/env bash
#
# Nasha AI — safe local build helper.
#
# This script does NOT download anything, does NOT generate the Gradle
# wrapper JAR, and does NOT sign anything. If the wrapper files are missing
# it stops with a clear message.
#
# Usage:
#   ./build.sh             # assembleDebug + test
#   ./build.sh release     # assembleRelease (unsigned; signing separate)
#   ./build.sh zip         # assembleDebug + test + zip source

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

MODE="${1:-debug}"

# ---- Preflight ----------------------------------------------------------

if [[ ! -f "./gradlew" ]]; then
  echo "ERROR: ./gradlew is missing."
  echo "       The Gradle wrapper JAR and shell script are NOT included in this"
  echo "       source drop (they are binary and generated per-project)."
  echo ""
  echo "       Generate them once with a locally installed Gradle 8.9:"
  echo "         gradle wrapper --gradle-version 8.9"
  echo "         chmod +x gradlew"
  echo ""
  echo "       Then re-run ./build.sh."
  exit 1
fi

if [[ ! -f "./gradlew.bat" ]]; then
  echo "WARNING: ./gradlew.bat is missing (Windows users need it)."
fi

if [[ ! -f "./gradle/wrapper/gradle-wrapper.jar" ]]; then
  echo "ERROR: gradle/wrapper/gradle-wrapper.jar is missing."
  echo "       Run 'gradle wrapper --gradle-version 8.9' to generate it."
  exit 1
fi

if [[ ! -f "./local.properties" ]]; then
  echo "WARNING: local.properties is missing."
  echo "         Copy local.properties.example to local.properties and set"
  echo "         sdk.dir=/path/to/Android/Sdk."
  echo ""
fi

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: 'java' is not on PATH. Nasha AI requires JDK 17."
  exit 1
fi

JAVA_VERSION_RAW="$(java -version 2>&1 | head -n1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "${JAVA_VERSION_RAW:-0}" -lt 17 ]]; then
  echo "ERROR: JDK 17 is required (found ${JAVA_VERSION_RAW:-unknown})."
  exit 1
fi

# ---- Build --------------------------------------------------------------

case "$MODE" in
  debug)
    echo ">>> ./gradlew clean assembleDebug test"
    ./gradlew clean assembleDebug test
    echo ""
    echo "APK: app/build/outputs/apk/debug/app-debug.apk"
    ;;

  release)
    echo ">>> ./gradlew clean assembleRelease"
    ./gradlew clean assembleRelease
    echo ""
    echo "Unsigned APK: app/build/outputs/apk/release/app-release-unsigned.apk"
    echo "You must sign it yourself with your own keystore."
    ;;

  zip)
    echo ">>> ./gradlew clean assembleDebug test"
    ./gradlew clean assembleDebug test
    echo ""
    echo ">>> zipping source (excluding build/, .gradle/, local.properties)"
    ZIP_NAME="NashaAI-source.zip"
    rm -f "$ZIP_NAME"
    if command -v zip >/dev/null 2>&1; then
      zip -r "$ZIP_NAME" . \
        -x "*/build/*" \
        -x "build/*" \
        -x "*/.gradle/*" \
        -x ".gradle/*" \
        -x "*/.idea/*" \
        -x ".idea/*" \
        -x "*/local.properties" \
        -x "local.properties" \
        -x "*.iml" \
        -x "*/NashaAI-source.zip" \
        -x "NashaAI-source.zip"
    else
      tar --exclude='*/build' --exclude='build' \
          --exclude='*/.gradle' --exclude='.gradle' \
          --exclude='*/.idea' --exclude='.idea' \
          --exclude='*/local.properties' --exclude='local.properties' \
          --exclude='*.iml' --exclude='NashaAI-source.zip' \
          -czf "${ZIP_NAME%.zip}.tar.gz" .
    fi
    echo "Source archive: $ZIP_NAME (or .tar.gz fallback)"
    ;;

  *)
    echo "Unknown mode: $MODE"
    echo "Usage: ./build.sh [debug|release|zip]"
    exit 2
    ;;
esac

echo ""
echo "Done."
