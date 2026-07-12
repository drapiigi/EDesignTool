#!/usr/bin/env bash
# Build a Linux application image with jpackage (requires JDK 21+ with jpackage).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

export PATH="${HOME}/.local/bin:${PATH}"

echo "==> Maven package (shaded jar)"
mvn -q -DskipTests package

JAR=$(ls -1 target/gwire-designer-*-SNAPSHOT.jar 2>/dev/null | grep -v original | head -1)
if [[ -z "${JAR}" ]]; then
  JAR=$(ls -1 target/gwire-designer-*.jar 2>/dev/null | grep -v original | head -1)
fi
if [[ -z "${JAR}" || ! -f "${JAR}" ]]; then
  echo "ERROR: shaded jar not found under target/"
  exit 1
fi

VERSION=$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version)
APP_NAME="GhanaWireAI"
DEST="target/dist/linux"
rm -rf "${DEST}"
mkdir -p "${DEST}"

if ! command -v jpackage >/dev/null 2>&1; then
  echo "ERROR: jpackage not found. Install a full JDK 21+ (not JRE only)."
  exit 1
fi

echo "==> jpackage app-image from ${JAR}"
# Runtime image includes JavaFX modules if present on module path; for shaded jar we use
# --input + main-jar so native JavaFX libs from the jar/dependencies are used via classpath.
# Note: JavaFX often needs platform natives; prefer running via 'mvn javafx:run' for dev.
# This packaging ships the shaded fat jar with a bundled JVM for convenience.

INPUT_DIR="target/jpackage-input"
rm -rf "${INPUT_DIR}"
mkdir -p "${INPUT_DIR}"
cp "${JAR}" "${INPUT_DIR}/gwire-designer.jar"
# Ship sample project next to the jar for end users
mkdir -p "${INPUT_DIR}/samples"
cp -f src/main/resources/samples/ghana-3bed-house.gwire "${INPUT_DIR}/samples/" 2>/dev/null || true

jpackage \
  --type app-image \
  --name "${APP_NAME}" \
  --app-version "${VERSION//-SNAPSHOT/}" \
  --description "GhanaWire AI — electrical wiring design for Ghana (L.I. 2008)" \
  --vendor "GhanaWire AI" \
  --input "${INPUT_DIR}" \
  --main-jar gwire-designer.jar \
  --main-class com.ghana.gwire.Main \
  --dest "${DEST}" \
  --java-options "-Dfile.encoding=UTF-8" \
  --java-options "--enable-native-access=ALL-UNNAMED"

echo ""
echo "Done. App image: ${DEST}/${APP_NAME}"
echo "Run: ${DEST}/${APP_NAME}/bin/${APP_NAME}"
echo ""
echo "Optional .deb (needs dpkg tools):"
echo "  jpackage --type deb --name ${APP_NAME} --app-version ${VERSION//-SNAPSHOT/} \\"
echo "    --input ${INPUT_DIR} --main-jar gwire-designer.jar --main-class com.ghana.gwire.Main \\"
echo "    --dest ${DEST}"
