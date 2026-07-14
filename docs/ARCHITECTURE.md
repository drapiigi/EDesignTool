# GhanaWire AI — architecture (Phase 16)

## Domain package boundaries

| Package | Responsibility |
|---------|----------------|
| `domain.geometry` | `Vec2`, `Segment2`, **`SpatialIndex`** — pure geometry, no UI |
| `domain.floorplan` | Walls, rooms, openings, wiring routes, **dimensions**, background |
| `domain.components` | Catalogue `ElectricalComponent`, `PlacedDevice` |
| `domain.electrical` | Persistent **Circuit**, **ConsumerUnit**, checklist, heights |
| `domain.calc` | `CircuitLoad`, `DesignReport`, validation DTOs |
| `domain.project` | `Project`, storeys, settings |

Further physical package renames are avoided to keep import stability; logical boundaries above are the GA contract.

## Services

| Package | Role |
|---------|------|
| `service.calc` | Load engine, diversity, cables, goldens |
| `service.electrical` | Circuit materialization |
| `service.cad` | Command parser, **DXF** I/O |
| `service.persist` | `.gwire` / `.gwirez` (format **1.4**) |
| `service.export` | PDF / Excel |
| `service.telemetry` | Opt-in only |
| `service.security` | `SecretStore` interface + file / keyring stub |

## UI

| Area | Notes |
|------|-------|
| Canvas | Tools, grips, layers, OSNAP, AI ghosts |
| Right dock tabs | Inspector · Results · Circuits · BOQ · **AI Chat** |
| Symbols | Drag-and-drop catalogue |

## Performance

- Viewport culling of symbols (Phase 10)
- **Spatial index** for device/wall hit-testing when counts ≥ 80 (Phase 16)
