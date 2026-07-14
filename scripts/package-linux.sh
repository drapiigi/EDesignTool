#!/usr/bin/env bash
# Build a Linux application image with jpackage (requires JDK 21+ with jpackage).
# Optional .deb when dpkg/fakeroot tooling is available.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export PATH="${HOME}/.local/bin:${PATH}"

echo "==> Maven package (shaded jar)"
mvn -q -DskipTests package

JAR=$(ls -1 target/gwire-designer-*.jar 2>/dev/null | grep -v original | head -1)
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "ERROR: shaded jar not found under target/"
  exit 1
fi

VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version 2>/dev/null || echo "0.9.0")
APP_VERSION="${VERSION//-SNAPSHOT/}"
APP_NAME="GhanaWireAI"
DEST="target/dist/linux"
rm -rf "${DEST}"
mkdir -p "${DEST}"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "ERROR: jpackage not found. Install a full JDK 21+ (not JRE only)."
  exit 1
fi

echo "==> jpackage app-image from ${JAR} (version ${APP_VERSION})"
INPUT_DIR="target/jpackage-input"
rm -rf "${INPUT_DIR}"
mkdir -p "${INPUT_DIR}/samples"
cp "${JAR}" "${INPUT_DIR}/gwire-designer.jar"
cp -f src/main/resources/samples/ghana-3bed-house.gwire "${INPUT_DIR}/samples/" 2>/dev/null || true
cp -f LICENSE "${INPUT_DIR}/" 2>/dev/null || true
cp -f docs/USER_GUIDE.md "${INPUT_DIR}/" 2>/dev/null || true

jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --description "GhanaWire AI — electrical wiring design for Ghana (L.I. 2008) — preliminary design aid" \
  --vendor "GhanaWire AI" \
  --copyright "See LICENSE" \
  --input "${INPUT_DIR}" \
  --main-jar gwire-designer.jar \
  --main-class com.ghana.gwire.Main \
  --dest "${DEST}" \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=ALL-UNNAMED" \
  --java-options "-Dgwire.build.signed=${GWIRE_BUILD_SIGNED:-false}"

# Friendly versioned directory name for release notes
if [[ -d "${DEST}/${APP_NAME}" ]]; then
  VERSIONED="${DEST}/${APP_NAME}-${APP_VERSION}-linux-x64"
  rm -rf "${VERSIONED}"
  cp -a "${DEST}/${APP_NAME}" "${VERSIONED}"
  echo "Versioned copy: ${VERSIONED}"
fi

echo ""
echo "Done. App image: ${DEST}/${APP_NAME}"
echo "Run: ${DEST}/${APP_NAME}/bin/${APP_NAME}"
echo "Artifact names: GhanaWireAI/ · GhanaWireAI-${APP_VERSION}-linux-x64/"
echo ""

# Best-effort .deb
if command -v fakeroot >/dev/null 2>&1 || command -v dpkg-deb >/dev/null 2>&1; then
  echo "==> Attempting .deb (best-effort)"
  set +e
  jpackage \
    --type deb \
    --name "${APP_NAME}" \
    --app-version "${APP_VERSION}" \
    --description "GhanaWire AI electrical design (L.I. 2008 practice) — design aid only" \
    --vendor "GhanaWire AI" \
    --input "${INPUT_DIR}" \
    --main-jar gwire-designer.jar \
    --main-class com.ghana.gwire.Main \
    --dest "${DEST}" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "--enable-native-access=ALL-UNNAMED"
  DEB_RC=$?
  set -e
  if [[ ${DEB_RC} -eq 0 ]]; then
    echo "DEB: ${DEST}/"
    ls -la "${DEST}"/*.deb 2>/dev/null || true
  else
    echo "Note: .deb packaging failed or skipped (install fakeroot/binutils/dpkg tools)."
  fi
else
  echo "Note: .deb skipped (fakeroot/dpkg not found). App-image is the required beta artifact."
fi

echo ""
echo "QA smoke: launch app-image → Help → Sample 3-bed → Tools → Recalculate → File → Export PDF"
echo "Code signing is optional for beta; set GWIRE_BUILD_SIGNED=true when shipping signed builds."
