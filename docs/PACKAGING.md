# Packaging GhanaWire AI (beta 0.9)

## Artifact names

| Platform | Path |
|----------|------|
| Linux app-image | `target/dist/linux/GhanaWireAI/` |
| Linux versioned | `target/dist/linux/GhanaWireAI-1.0.0-linux-x64/` |
| Linux tarball | `target/dist/linux/GhanaWireAI-linux-x64.tar.gz` |
| Linux .deb | `target/dist/linux/*.deb` (**best-effort**) |
| Windows app-image | `target/dist/windows/GhanaWireAI/` |
| Windows versioned | `target/dist/windows/GhanaWireAI-1.0.0-windows-x64/` |
| Windows MSI | `target/dist/windows/GhanaWireAI-1.0.0.msi` (**best-effort**, needs WiX) |

Beta gate: **Linux app-image required**. Deb/MSI optional.

## Development run

```bash
cd /path/to/EDesignTool
export PATH="$HOME/.local/bin:$PATH"
mvn javafx:run
```

## Fat JAR

```bash
mvn -DskipTests package
java --enable-native-access=ALL-UNNAMED -jar target/gwire-designer-1.0.0.jar
```

> JavaFX natives are platform-specific. Prefer `mvn javafx:run` for development and jpackage for end users.

## Linux app-image (required for beta)

Requires **JDK 21+** with `jpackage`.

```bash
chmod +x scripts/package-linux.sh
./scripts/package-linux.sh
# Run: target/dist/linux/GhanaWireAI/bin/GhanaWireAI
```

Optional `.deb` is attempted when `fakeroot` / `dpkg` tools exist; failure does not fail the script.

## Windows app-image

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1
```

### WiX (MSI best-effort)

| Need | Notes |
|------|--------|
| WiX Toolset 3.x or 4.x | On `PATH` (`candle`/`light` or `wix`) |
| Without WiX | Script still ships **app-image**; MSI step is skipped with a warning |

## Portable tarball / AppImage

```bash
./scripts/package-appimage.sh
```

## Unsigned beta

Builds default to **unsigned**. The first-run dialog shows an unsigned notice.

| Flag | Effect |
|------|--------|
| `-Dgwire.build.signed=true` (JVM) | About dialog says signed |
| `GWIRE_BUILD_SIGNED=true` (packaging env) | Passed into packaged app |

Publish **SHA-256 checksums** next to release artifacts when available.

## Code signing (optional)

| Platform | Tooling |
|----------|---------|
| Windows | `signtool` / Azure Trusted Signing after MSI |
| macOS | `jpackage --mac-sign` (not in beta gate) |
| Linux .deb | `debsigs` |

## Update check

App fetches `docs/release/version.json` (override with `GWIRE_UPDATE_URL` or prefs).

```json
{ "latest": "0.9.1", "notes": "…", "url": "https://github.com/drapiigi/EDesignTool/releases" }
```

Non-blocking: status bar message only.

## QA smoke matrix

| Step | Action |
|------|--------|
| 1 | Launch app-image (Ubuntu LTS or Windows 10/11) |
| 2 | Accept first-run disclaimer |
| 3 | Help → Open Sample 3-Bed House |
| 4 | Tools → Recalculate Loads |
| 5 | File → Export PDF Report |
| 6 | File → Save as `.gwire` |

## Sample project

Bundled: `src/main/resources/samples/ghana-3bed-house.gwire`  
In app: **Help → Open Sample 3-Bed House**.

## CI package job (example)

Copy [`docs/ci/package.yml.example`](ci/package.yml.example) to `.github/workflows/package.yml` when your GitHub token has the `workflow` scope.

