#!/usr/bin/env bash
set -euo pipefail

PROJECT_PATH="${1:-SampleApp.xcodeproj}"
SCHEME="${2:-SampleApp}"
EXPORT_METHOD="${3:-ad-hoc}"
OUTPUT_DIR="${4:-build/ipa}"
ARCHIVE_PATH="${OUTPUT_DIR}/${SCHEME}.xcarchive"

mkdir -p "$OUTPUT_DIR"

if [ ! -d "$PROJECT_PATH" ] && [ ! -f "$PROJECT_PATH" ]; then
  echo "Project path not found: $PROJECT_PATH" >&2
  exit 1
fi

if [ -d "$PROJECT_PATH" ]; then
  PROJECT_ARG=(-project "$PROJECT_PATH")
else
  PROJECT_ARG=(-project "$PROJECT_PATH")
fi

xcodebuild "${PROJECT_ARG[@]}" -scheme "$SCHEME" -configuration Release -archivePath "$ARCHIVE_PATH" archive
xcodebuild -exportArchive -archivePath "$ARCHIVE_PATH" -exportPath "$OUTPUT_DIR" -exportOptionsPlist exportOptions.plist

echo "IPA generated in $OUTPUT_DIR"
