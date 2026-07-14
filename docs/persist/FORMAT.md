# GhanaWire project format

## Versions

| Version | Description |
|---------|-------------|
| **1.0** | Single floor plan (rooms, walls, openings, devices, background path) |
| **1.1** | Multi-storey + wiring routes |
| **1.2** | Package embeds (`embeddedRef`, `mediaHash`) for `.gwirez` |
| **1.3** | **Current write version:** first-class circuits, consumer unit, checklist, device `circuitId` + `mountingHeightMm` |

Loaders accept any `1.*` file. **All new saves write `formatVersion: 1.3`.**

## Extensions

| Extension | Contents |
|-----------|----------|
| **`.gwire`** | Pretty-printed JSON (default Save / Save As) |
| **`.gwirez`** | ZIP package: `project.json` + `media/{ref}` for embedded backgrounds |

## Format 1.3 fields

### Root

| Field | Type | Notes |
|-------|------|-------|
| `circuits` | array | Persistent final circuits (stable `id`) |
| `consumerUnit` | object \| omit | Ways, incomer A, RCD description, `wayCircuitIds` |
| `checklistReview` | object | Map of validation code → `{ reviewed, note }` |

### Circuit object

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | Stable UUID across save/load |
| `name` | string | Display / SLD label |
| `kind` | enum | `LIGHTING`, `SOCKET`, `COOKER`, … (`CircuitKind`) |
| `roomId` | string \| omit | Optional room association |
| `deviceIds` | string[] | Placed device membership |
| `wayNumber` | int | 1-based CU way; 0 = unassigned |
| `rcdGroup` | string | e.g. `RCD-A` |
| `breakerA` | number | MCB rating (A) |
| `cableComponentId` | string | Catalogue cable id |
| `cableSize` | string | e.g. `2.5 mm2` |
| `estimatedLengthM` | number | Cable length estimate |
| `notes` | string | Free text |

### Consumer unit object

| Field | Type | Notes |
|-------|------|-------|
| `id`, `name` | string | Board identity |
| `ways` | int | Way count (min 4) |
| `incomerA` | number | Main switch / isolator rating |
| `rcdDescription` | string | e.g. `RCCB 63 A 30 mA` |
| `wayCircuitIds` | (string\|null)[] | Ordered circuit ids (index 0 = way 1) |

### Device extras (inside each floor plan)

| Field | Type | Notes |
|-------|------|-------|
| `circuitId` | string \| omit | FK to `circuits[].id` |
| `mountingHeightMm` | number \| omit | Height above finished floor (mm) |

### Pre-1.3 upgrade behaviour

1. Files without `circuits` load with an empty circuit list.
2. On first **Tools → Recalculate Loads**, `CircuitMaterializer` builds circuits from devices via `CircuitBuilder`, assigns CU ways, applies default mounting heights, and remaps `WiringRoute.circuitId` (by ephemeral load id or label match).
3. Dangling route `circuitId` values that cannot be remapped are cleared.

Calculation reports (`lastReport`) are **not** persisted — re-run recalculate after open.

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
