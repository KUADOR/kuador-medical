#!/usr/bin/env bash
# Renders the Flutter goldens on Linux with the Flutter version CI pins (job «movil»), so the
# baselines match CI. Usage (from movil/): scripts/update-goldens.sh
# Requires Docker. The SDK is cloned once into the Docker volume «medical-kuador-flutter-sdk».
set -euo pipefail

FLUTTER_VERSION="3.47.9"
MOVIL_DIR="$(cd "$(dirname "$0")/.." && pwd)"

docker run --rm --platform linux/amd64 \
  -e FLUTTER_VERSION="${FLUTTER_VERSION}" \
  -v medical-kuador-flutter-sdk:/sdk \
  -v "${MOVIL_DIR}:/app" \
  -w /app \
  ubuntu:24.04 \
  bash -c '
    set -euo pipefail
    apt-get update -qq >/dev/null
    apt-get install -y -qq git curl unzip xz-utils ca-certificates >/dev/null
    if [ ! -d "/sdk/${FLUTTER_VERSION}" ]; then
      git clone -q --depth 1 --branch "${FLUTTER_VERSION}" https://github.com/flutter/flutter.git "/sdk/${FLUTTER_VERSION}"
    fi
    export PATH="/sdk/${FLUTTER_VERSION}/bin:${PATH}"
    git config --global --add safe.directory "*"
    flutter pub get
    flutter test --tags golden --update-goldens
  '
