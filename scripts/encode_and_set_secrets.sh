#!/usr/bin/env bash
set -euo pipefail

usage(){
  cat <<EOF
Usage: $0 <path-to-certificate.p12> <p12-password> <path-to-profile.mobileprovision> [github_repo]

If optional [github_repo] (owner/repo) is provided and GH_TOKEN is set in environment,
the script will attempt to set GitHub repo secrets: P12_BASE64, P12_PASSWORD, PROVISION_BASE64.
Otherwise it prints the base64 values and example gh commands.
EOF
}

if [ "$#" -lt 3 ]; then
  usage
  exit 1
fi

P12_FILE="$1"
P12_PASS="$2"
PROV_FILE="$3"
GITHUB_REPO="${4-}"

if [ ! -f "$P12_FILE" ]; then echo "Certificate not found: $P12_FILE" >&2; exit 2; fi
if [ ! -f "$PROV_FILE" ]; then echo "Provisioning profile not found: $PROV_FILE" >&2; exit 3; fi

P12_BASE64=$(base64 -w 0 "$P12_FILE")
PROV_BASE64=$(base64 -w 0 "$PROV_FILE")

echo "P12_BASE64 (first 200 chars): ${P12_BASE64:0:200}..."
echo "PROVISION_BASE64 (first 200 chars): ${PROV_BASE64:0:200}..."

if [ -n "$GITHUB_REPO" ] && [ -n "${GH_TOKEN-}" ]; then
  echo "Uploading secrets to GitHub repo: $GITHUB_REPO"
  export GITHUB_TOKEN="$GH_TOKEN"
  gh secret set P12_BASE64 -b "$P12_BASE64" -R "$GITHUB_REPO"
  gh secret set P12_PASSWORD -b "$P12_PASS" -R "$GITHUB_REPO"
  gh secret set PROVISION_BASE64 -b "$PROV_BASE64" -R "$GITHUB_REPO"
  echo "Secrets set: P12_BASE64, P12_PASSWORD, PROVISION_BASE64"
else
  echo "Run these commands to set secrets manually (or provide GH_TOKEN and repo to auto-set):"
  echo
  echo "gh secret set P12_BASE64 -b '<paste P12_BASE64 here>' -R <owner/repo>"
  echo "gh secret set P12_PASSWORD -b '$P12_PASS' -R <owner/repo>"
  echo "gh secret set PROVISION_BASE64 -b '<paste PROVISION_BASE64 here>' -R <owner/repo>"
  echo
  echo "Or save values to files and use the UI to create secrets."
fi
