#!/usr/bin/env bash
set -euo pipefail
# Usage:
# GH_TOKEN=xxx ./scripts/auto_release.sh owner/repo /path/to/cert.p12 p12password /path/to/profile.mobileprovision v1.0.0 [apple_id] [apple_specific_password]

if [ "$#" -lt 5 ]; then
  echo "Usage: GH_TOKEN=... $0 owner/repo p12_path p12_password prov_path tag [apple_id] [apple_specific_password]"
  exit 1
fi

REPO="$1"
P12_PATH="$2"
P12_PASS="$3"
PROV_PATH="$4"
TAG="$5"
APPLE_ID="${6:-}"
APPLE_SPEC_PASS="${7:-}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI not found. Install from https://cli.github.com/" >&2
  exit 1
fi

if ! command -v base64 >/dev/null 2>&1; then
  echo "base64 not found. Install coreutils or use an environment with base64." >&2
  exit 1
fi

if [ ! -f "$P12_PATH" ]; then
  echo "P12 not found: $P12_PATH" >&2
  exit 1
fi
if [ ! -f "$PROV_PATH" ]; then
  echo "Provisioning profile not found: $PROV_PATH" >&2
  exit 1
fi

if [ -z "${GH_TOKEN-}" ]; then
  echo "Export GH_TOKEN as an env var (personal access token with 'repo' and 'workflow' scopes)." >&2
  exit 1
fi

echo "Authenticating gh CLI..."
echo "$GH_TOKEN" | gh auth login --with-token >/dev/null

echo "Encoding files..."
P12_B64=$(base64 -w0 "$P12_PATH")
PROV_B64=$(base64 -w0 "$PROV_PATH")

echo "Setting repository secrets..."
echo "$P12_B64" | gh secret set P12_BASE64 -R "$REPO" -b -
gh secret set P12_PASSWORD -R "$REPO" -b "$P12_PASS"
echo "$PROV_B64" | gh secret set PROVISION_BASE64 -R "$REPO" -b -

if [ -n "$APPLE_ID" ]; then
  gh secret set APPLE_ID -R "$REPO" -b "$APPLE_ID"
fi
if [ -n "$APPLE_SPEC_PASS" ]; then
  gh secret set APPLE_SPECIFIC_PASSWORD -R "$REPO" -b "$APPLE_SPEC_PASS"
fi

echo "Secrets set. Creating and pushing tag '$TAG'..."
git fetch --tags origin || true
git tag -f "$TAG"
git push origin --tags --force

echo "Done. Workflow should be triggered on GitHub Actions."
echo "Open: https://github.com/$REPO/actions to monitor run and download artifact 'ios-ipa'."
