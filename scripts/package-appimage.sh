#!/usr/bin/env bash
# Best-effort AppImage-style layout: builds the Linux app-image then wraps a portable tarball.
# Full AppImage tooling (appimagetool) is optional if installed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./scripts/package-linux.sh

APP_DIR="target/dist/linux/GhanaWireAI"
if [[ ! -d "${APP_DIR}" ]]; then
  echo "ERROR: expected ${APP_DIR} from package-linux.sh"
  exit 1
fi

OUT="target/dist/linux"
TAR="${OUT}/GhanaWireAI-linux-x64.tar.gz"
tar -czf "${TAR}" -C "${OUT}" GhanaWireAI
echo "Portable archive: ${TAR}"

if command -v appimagetool >/dev/null 2>&1; then
  echo "appimagetool found — building AppImage (unsigned)"
  # Minimal AppDir layout for appimagetool users who have it installed
  APPDIR="${OUT}/GhanaWireAI.AppDir"
  rm -rf "${APPDIR}"
  mkdir -p "${APPDIR}/usr"
  cp -a "${APP_DIR}/." "${APPDIR}/usr/"
  cat > "${APPDIR}/AppRun" << 'EOF'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/GhanaWireAI" "$@"
EOF
  chmod +x "${APPDIR}/AppRun"
  cat > "${APPDIR}/GhanaWireAI.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=GhanaWire AI
Exec=GhanaWireAI
Icon=gwire
Categories=Engineering;
EOF
  appimagetool "${APPDIR}" "${OUT}/GhanaWireAI-x86_64.AppImage" || true
  echo "If appimagetool succeeded: ${OUT}/GhanaWireAI-x86_64.AppImage"
else
  echo "Note: appimagetool not installed — shipped portable .tar.gz instead."
  echo "Code signing requires your own certificate (jpackage --mac-sign / signtool / debsigs)."
fi
