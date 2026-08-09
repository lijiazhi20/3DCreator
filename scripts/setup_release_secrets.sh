#!/usr/bin/env bash
# Push the four release-signing secrets to the GitHub repository via the `gh` CLI.
# This makes the cloud CI (`.github/workflows/build.yml`) build a SIGNED release APK.
#
# Prerequisites:
#   - `gh` CLI installed and authenticated:  gh auth login
#   - a GitHub repo linked (the workflow's `secrets.*` resolve to THIS repo)
#   - the keystore files exist (see BUILD.md §5 / scripts/load_release_env.sh)
#
# What it sets (Settings -> Secrets -> Actions):
#   KEYSTORE_BASE64      base64 of android/app/release-key.jks
#   KEY_ALIAS            key alias
#   KEY_PASSWORD         key password
#   KEYSTORE_PASSWORD    keystore password
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
B64="$DIR/android/app/release-key.b64.txt"
PROPS="$DIR/android/app/release-key.properties"

if [[ ! -f "$B64" || ! -f "$PROPS" ]]; then
  echo "ERROR: keystore artifacts missing. Generate them first (see BUILD.md §5)." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: 'gh' CLI not found. Install it and run 'gh auth login' first." >&2
  exit 1
fi

gh secret set KEYSTORE_BASE64 < "$B64"
gh secret set KEY_ALIAS < <(grep '^KEY_ALIAS=' "$PROPS" | cut -d= -f2)
gh secret set KEY_PASSWORD < <(grep '^KEY_PASSWORD=' "$PROPS" | cut -d= -f2)
gh secret set KEYSTORE_PASSWORD < <(grep '^KEYSTORE_PASSWORD=' "$PROPS" | cut -d= -f2)

echo "✅ Release-signing secrets pushed to GitHub."
echo "   Future cloud builds will produce a SIGNED app-release.apk."
echo "   Trigger a build: push to main, open a PR, or 'gh workflow run build.yml'."
