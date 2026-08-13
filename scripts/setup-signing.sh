#!/bin/bash
# Creates the release signing key and uploads it to GitHub as repository secrets.
#
# Run once. The key never enters the repository, the terminal output, or any log:
# it is written to a file you keep, and pushed straight to GitHub's secret store.
#
# Losing this key means you can never publish an update that installs over the
# current one — back it up somewhere durable before you ship to anyone.
set -euo pipefail

KEYSTORE="${1:-$HOME/moonlight-release.keystore}"
ALIAS="moonlight"
REPO="kiineld/moonlightvpn_android"

if [ -f "$KEYSTORE" ]; then
  echo "Using the existing key at $KEYSTORE"
else
  echo "Creating a new release key at $KEYSTORE"
  echo "Choose a password you can retrieve later — it is needed for every release."
  read -r -s -p "Keystore password: " PASSWORD; echo
  read -r -s -p "Confirm: " CONFIRM; echo
  [ "$PASSWORD" = "$CONFIRM" ] || { echo "Passwords do not match."; exit 1; }
  [ ${#PASSWORD} -ge 12 ] || { echo "Use at least 12 characters."; exit 1; }

  keytool -genkeypair -v \
    -keystore "$KEYSTORE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 4096 \
    -validity 10950 \
    -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -dname "CN=Moonlight VPN, O=Moonlight, C=US"
  chmod 600 "$KEYSTORE"
fi

if [ -z "${PASSWORD:-}" ]; then
  read -r -s -p "Keystore password: " PASSWORD; echo
fi

if ! command -v gh > /dev/null; then
  cat <<EOF

The GitHub CLI is not installed, so the secrets have to be added by hand at
  https://github.com/$REPO/settings/secrets/actions

  MOONLIGHT_KEYSTORE_BASE64    the output of:  base64 -i "$KEYSTORE"
  MOONLIGHT_KEYSTORE_PASSWORD  the password you just chose
  MOONLIGHT_KEY_ALIAS          $ALIAS
  MOONLIGHT_KEY_PASSWORD       the same password

Do not paste the base64 anywhere else — it is the private key.
EOF
  exit 0
fi

echo "Uploading secrets to $REPO"
base64 -i "$KEYSTORE" | gh secret set MOONLIGHT_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set MOONLIGHT_KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS"    | gh secret set MOONLIGHT_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASSWORD" | gh secret set MOONLIGHT_KEY_PASSWORD --repo "$REPO"

cat <<EOF

Done. Four secrets are set and the key stays at:
  $KEYSTORE

Back that file up. Then publish:
  git tag v1.0.5 && git push origin v1.0.5
EOF
