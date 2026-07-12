# Packaging GhanaWire AI

## Development run (recommended)

```bash
cd /path/to/EDesignTool
export PATH="$HOME/.local/bin:$PATH"
mvn javafx:run
```

## Fat JAR

```bash
mvn -DskipTests package
java --enable-native-access=ALL-UNNAMED -jar target/gwire-designer-*-SNAPSHOT.jar
```

> JavaFX native libraries are platform-specific. On some systems the shaded jar may need the matching JavaFX native jars on the module path. Prefer `mvn javafx:run` or jpackage for end users.

## Linux application image (jpackage)

Requires **JDK 21+** with `jpackage` (not a JRE-only install).

```bash
chmod +x scripts/package-linux.sh
./scripts/package-linux.sh
# Output: target/dist/linux/GhanaWireAI/
```

Optional Debian package:

```bash
# After package-linux.sh prepared target/jpackage-input
jpackage --type deb --name GhanaWireAI --app-version 0.1.0 \
  --input target/jpackage-input --main-jar gwire-designer.jar \
  --main-class com.ghana.gwire.Main --dest target/dist/linux
```

## Windows application image

```powershell
powershell -ExecutionPolicy Bypass -File scripts/package-windows.ps1
# Output: target\dist\windows\GhanaWireAI\
```

Optional MSI: use `jpackage --type msi` with the same `--input` / `--main-jar` flags.

## Sample project

Bundled resource:

- `src/main/resources/samples/ghana-3bed-house.gwire`

In the app: **Help → Open Sample 3-Bed House**.

Regenerate the sample file after factory changes:

```bash
mvn -q -DskipTests compile
# use ProjectStore + SampleProjectFactory (see scripts or unit tests)
```

## Notes

- Component library is created at first run under `~/.gwire/library`
- AI keys: `~/.gwire/ai.properties` or `GWIRE_AI_API_KEY` (never commit)
- PDF/Excel export requires a writable destination folder
