# GhanaWire project format

## Versions

| Version | Description |
|---------|-------------|
| **1.0** | Single floor plan (rooms, walls, openings, devices, background path) |
| **1.1** | Multi-storey + wiring routes |
| **1.2** | Current write version: optional package embeds (`embeddedRef`, `mediaHash`) |

Loaders accept any `1.*` file. After Phase 10, **all new saves write `formatVersion: 1.2`**.

## Extensions

| Extension | Contents |
|-----------|----------|
| **`.gwire`** | Pretty-printed JSON (default Save / Save As) |
| **`.gwirez`** | ZIP package: `project.json` + `media/{ref}` for embedded backgrounds |

## Atomic save

Saves write to `{path}.tmp`, fsync best-effort, then atomic move onto the primary path. Rolling backups: `{path}.bak`, `{path}.bak2`.

## Autosave

- Path: `~/.gwire/autosave/{projectId}.gwire`
- Interval: 5 minutes while dirty
- Full multi-storey project
- Clean-exit marker: `~/.gwire/clean-exit` (written on graceful quit; recovery offered only if missing)

## Secrets

- API keys: `~/.gwire/secrets.properties` (mode `0600`)
- Non-secret AI prefs: `~/.gwire/ai.properties`
- Env (`GWIRE_AI_API_KEY`, etc.) always wins
