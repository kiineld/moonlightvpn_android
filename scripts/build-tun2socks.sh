#!/bin/bash
# Builds hev-socks5-tunnel as a JNI shared library for all four Android ABIs.
#
# Why from source rather than the release binaries: the published Android
# artifacts are the STANDALONE EXECUTABLE, whose usage is `hev-socks5-tunnel
# CONFIG_PATH`. It creates its own tun device from the config, which needs root
# and cannot accept the file descriptor VpnService hands us. Only the library
# build (-DENABLE_LIBRARY) exposes hev_socks5_tunnel_main_from_file(path, fd)
# and the JNI entry points we actually need.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HEV_VERSION=2.17.1
NDK_VERSION=27.3.13750724
ANDROID_SDK="${ANDROID_HOME:-$HOME/android-sdk}"
NDK="$ANDROID_SDK/ndk/$NDK_VERSION"

# The JNI methods are registered against this exact class, so it must match
# vpn/moonlight/core/tunnel/Tun2Socks.kt.
JNI_PKG=vpn/moonlight/core/tunnel
JNI_CLS=Tun2Socks

WORK="$ROOT/.native-cache/hev-build"
OUT="$ROOT/vpn/src/main/jniLibs"
say(){ echo "=== [$(date +%H:%M:%S)] $* ==="; }

[ -x "$NDK/ndk-build" ] || { echo "NDK not found at $NDK"; exit 1; }

if [ ! -d "$WORK/jni/.git" ]; then
  say "cloning hev-socks5-tunnel $HEV_VERSION"
  rm -rf "$WORK"; mkdir -p "$WORK"
  git clone --quiet --recursive --depth 1 --branch "$HEV_VERSION" \
    https://github.com/heiher/hev-socks5-tunnel "$WORK/jni"
fi

say "ndk-build (PKGNAME=$JNI_PKG CLSNAME=$JNI_CLS)"
cd "$WORK"
"$NDK/ndk-build" \
  NDK_PROJECT_PATH="$WORK" \
  APP_BUILD_SCRIPT="$WORK/jni/Android.mk" \
  NDK_APPLICATION_MK="$WORK/jni/Application.mk" \
  APP_CFLAGS="-O3 -DPKGNAME=$JNI_PKG -DCLSNAME=$JNI_CLS" \
  hev-socks5-tunnel \
  -j"$(sysctl -n hw.ncpu)"

say "installing into jniLibs"
mkdir -p "$OUT"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  # Asking ndk-build for a single module skips its install step, so the output
  # stays in obj/local. Take whichever path exists.
  src="$WORK/libs/$abi/libhev-socks5-tunnel.so"
  [ -f "$src" ] || src="$WORK/obj/local/$abi/libhev-socks5-tunnel.so"
  [ -f "$src" ] || { echo "!! missing $abi"; exit 1; }
  mkdir -p "$OUT/$abi"
  cp "$src" "$OUT/$abi/libhev-socks5-tunnel.so"
  echo "  $abi $(du -h "$OUT/$abi/libhev-socks5-tunnel.so" | cut -f1)"
done

say "verifying the JNI class string is baked in"
expected="$JNI_PKG/$JNI_CLS"
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  # Two traps here: `strings` needs -a or it skips the section holding this
  # literal, and `grep -q` must be avoided because it exits on first match,
  # SIGPIPEs `strings`, and `set -o pipefail` then reports a match as failure.
  if strings -a "$OUT/$abi/libhev-socks5-tunnel.so" | grep -x "$expected" > /dev/null; then
    echo "  $abi OK -> $expected"
  else
    echo "!! $abi does not reference $expected"; exit 1
  fi
done
say "DONE"
