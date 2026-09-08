#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/.build/lwjgl"
OUT="$ROOT/app/src/main/assets/components/lwjgl3"

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT"

# Phase 4.3 uses the Android LWJGL work maintained by PojavLauncher as the
# compatibility layer. Keep the source repositories outside the Aether source
# tree; the generated runtime artifacts are what ship in the APK.
git clone --depth 1 --branch wip/rebase_3.3.3 https://github.com/PojavLauncherTeam/lwjgl3.git "$WORK/lwjgl3"
git clone --depth 1 --branch v3_openjdk https://github.com/PojavLauncherTeam/PojavLauncher.git "$WORK/pojav"

export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.0.12077973}"
export JAVA8_HOME="${JAVA8_HOME:-$JAVA_HOME}"
export LWJGL_BUILD_ARCH=arm64

if [[ ! -x "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang" ]]; then
  echo "Android NDK toolchain not found at $ANDROID_NDK_HOME" >&2
  exit 1
fi

# The custom LWJGL Android build requires Java 8 and Ant.
if ! command -v ant >/dev/null 2>&1; then
  echo "Apache Ant is required" >&2
  exit 1
fi

pushd "$WORK/lwjgl3" >/dev/null
JAVA_HOME="$JAVA8_HOME" bash ci_build_android.bash
popd >/dev/null

# Build the Android GLFW Java stub. It replaces the desktop GLFW implementation
# used by Minecraft 1.13+ while preserving the LWJGL GLFW API surface.
pushd "$WORK/pojav" >/dev/null
./gradlew --no-daemon :jre_lwjgl3glfw:build
popd >/dev/null

# Package custom LWJGL Java classes first so they shadow the official desktop
# LWJGL jars that Mojang downloads for Linux.
find "$WORK/lwjgl3/bin/RELEASE" -maxdepth 1 -type f -name '*.jar' \
  ! -name '*-natives-*' ! -name '*-sources.jar' -print -exec cp {} "$OUT/" \;

# Pojav's GLFW stub Gradle project writes its jar directly into the cloned
# launcher asset directory rather than jre_lwjgl3glfw/build/libs.
GLFW_JAR="$WORK/pojav/app_pojavlauncher/src/main/assets/components/lwjgl3/lwjgl-glfw-classes.jar"
if [[ ! -f "$GLFW_JAR" ]]; then
  echo "Android GLFW stub jar not found at $GLFW_JAR" >&2
  exit 1
fi
cp "$GLFW_JAR" "$OUT/lwjgl-glfw-classes.jar"

mkdir -p "$OUT/native/arm64-v8a"
find "$WORK/lwjgl3/bin/out" -maxdepth 1 -type f -name '*.so' -print -exec cp {} "$OUT/native/arm64-v8a/" \;

cat > "$OUT/README.txt" <<'EOF'
Aether Launcher Phase 4.3 Android LWJGL runtime.

The generated artifacts are built from:
- PojavLauncherTeam/lwjgl3 (LWJGL 3.3.3 Android fork)
- PojavLauncherTeam/PojavLauncher (Android GLFW Java stub)

See the upstream repositories for their respective license and third-party
notices. This directory contains generated runtime artifacts only.
EOF

echo "Prepared Android LWJGL runtime in $OUT"
