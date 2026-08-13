#!/bin/bash
# Downloads the prebuilt native artifacts and fonts the app needs.
# Everything fetched here is intentionally NOT committed (see .gitignore) —
# run this once after cloning.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
XRAY_VERSION=v26.7.28
DL="$ROOT/.native-cache"
mkdir -p "$DL"
say(){ echo "=== [$(date +%H:%M:%S)] $* ==="; }
fail=0

# ---------- libXray AAR (Xray-core 26.7.28 via gomobile) ----------
AAR_DIR="$ROOT/vpn/libs"
mkdir -p "$AAR_DIR"
if [ ! -f "$AAR_DIR/libXray.aar" ]; then
  say "libXray $XRAY_VERSION"
  U="https://github.com/XTLS/libXray/releases/download/$XRAY_VERSION/libxray-android.zip"
  if curl -fSL --retry 3 -o "$DL/libxray-android.zip" "$U"; then
    rm -rf "$DL/lx"; mkdir -p "$DL/lx"
    unzip -oq "$DL/libxray-android.zip" -d "$DL/lx"
    A=$(find "$DL/lx" -name '*.aar' | head -1)
    if [ -n "$A" ]; then cp "$A" "$AAR_DIR/libXray.aar"; say "-> $(du -h "$AAR_DIR/libXray.aar" | cut -f1) libXray.aar"
    else echo "!! no .aar inside libxray-android.zip"; find "$DL/lx" -maxdepth 2; fail=1; fi
  else echo "!! libXray download failed"; fail=1; fi
fi

# ---------- tun -> socks5 ----------
# Deliberately NOT downloaded. hev-socks5-tunnel's published Android artifacts
# are the standalone executable, whose usage is `hev-socks5-tunnel CONFIG_PATH`:
# it creates its own tun device from the config, which needs root and cannot
# accept the descriptor VpnService hands us. Only the library build exposes the
# JNI entry points we need, so it is compiled from source by
# scripts/build-tun2socks.sh (requires the Android NDK).
if [ ! -f "$ROOT/vpn/src/main/jniLibs/arm64-v8a/libhev-socks5-tunnel.so" ]; then
  echo
  echo ">> tun2socks not built yet — run: scripts/build-tun2socks.sh"
  echo
else
  say "tun2socks already built"
fi

# ---------- Geodata ----------
# Required, not optional: every config the Remnawave panel serves references
# geosite:/geoip: rules (category-ru, geoip:ru, youtube, vk, ...), and xray-core
# refuses to start a config whose routing rules it cannot resolve.
GEO_DIR="$ROOT/vpn/src/main/assets/geo"
mkdir -p "$GEO_DIR"
if [ ! -s "$GEO_DIR/geosite.dat" ]; then
  say "geosite.dat (v2fly domain-list-community)"
  curl -fSL --retry 3 -o "$GEO_DIR/geosite.dat" \
    "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat" \
    || { echo "!! geosite download failed"; fail=1; }
fi
if [ ! -s "$GEO_DIR/geoip.dat" ]; then
  say "geoip.dat (v2fly geoip)"
  curl -fSL --retry 3 -o "$GEO_DIR/geoip.dat" \
    "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat" \
    || { echo "!! geoip download failed"; fail=1; }
fi
for f in geosite.dat geoip.dat; do
  [ -s "$GEO_DIR/$f" ] && echo "  $f $(du -h "$GEO_DIR/$f" | cut -f1)"
done

# ---------- Fonts: Onest (UI/body) + Unbounded (display) ----------
FONT_DIR="$ROOT/design/src/main/res/font"
mkdir -p "$FONT_DIR"
fetch_font(){ # $1=dest basename  $2..=candidate urls
  local dest="$FONT_DIR/$1"; shift
  [ -f "$dest" ] && return 0
  for u in "$@"; do
    if curl -fSL --retry 2 -o "$dest" "$u" && [ -s "$dest" ]; then
      say "font -> $(basename "$dest") $(du -h "$dest" | cut -f1)"; return 0
    fi
  done
  echo "!! font download failed: $(basename "$dest")"; rm -f "$dest"; return 1
}
GF=https://raw.githubusercontent.com/google/fonts/main/ofl
fetch_font onest_variable.ttf     "$GF/onest/Onest%5Bwght%5D.ttf"         || fail=1
fetch_font unbounded_variable.ttf "$GF/unbounded/Unbounded%5Bwght%5D.ttf" || fail=1

say "DONE (fail=$fail)"
exit $fail
