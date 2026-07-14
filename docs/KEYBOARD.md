# GhanaWire AI — keyboard & command cheat sheet

## File

| Shortcut | Action |
|----------|--------|
| **Ctrl+N** | New empty project |
| **Ctrl+O** | Open `.gwire` / `.gwirez` |
| **Ctrl+S** | Save |
| **Ctrl+I** | Import floor plan background |
| **Ctrl+E** | Export PDF report |
| **Ctrl+Q** | Exit |

**File → New from Template:** 1-bed, 3-bed, 2-storey starters.

## Edit / View

| Shortcut | Action |
|----------|--------|
| **Ctrl+Z** / **Ctrl+Y** | Undo / redo (active storey) |
| **Delete** | Delete selection |
| **Ctrl+=** / **Ctrl+-** | Zoom in / out |
| **Ctrl+0** | Fit plan to window |
| **F8** | Ortho on/off |
| **F3** | Endpoint OSNAP on/off |

## Design / Tools

| Shortcut | Action |
|----------|--------|
| **Ctrl+K** | Focus **AI Chat** panel |
| **Ctrl+G** | AI Generate Design (ghost preview) |
| **Ctrl+R** | Recalculate loads |
| **Ctrl+L** | Validate standards (L.I. 2008 practice) |
| **Ctrl+;** | Focus CAD command line |

**Tools → Calibrate background scale** — two clicks + known length → `mmPerPixel`.  
**Tools → Price book…** — edit catalogue unit costs (GHS) in H2.

## Canvas interaction

| Input | Action |
|-------|--------|
| Empty drag / Space+drag / middle / right | Pan whole plan |
| Two-finger scroll | Pan |
| Ctrl+scroll / pinch | Zoom toward cursor |
| Shift (while drawing wall) | Temporary ortho |
| Esc | Cancel wall / room / calibrate |

## CAD command line (status bar)

| Command | Effect |
|---------|--------|
| `LINE` or `L` | Start Wall tool |
| `3500` or `3500mm` | Complete in-progress wall at that length (mm) |
| `3.5m` | Same in metres |
| `ORTHO ON` / `ORTHO OFF` | Ortho mode |
| `OSNAP ON` / `OSNAP OFF` | Endpoint snap |
| `CANCEL` / `X` | Cancel → Select tool |
| `HELP` / `?` | Show hint |

## AI preview (Phase 15)

1. **Design → AI Generate Design** builds a plan without modifying devices.
2. Ghost symbols appear on the canvas (cyan ring).
3. Click a ghost to toggle selection; dialog: Select all / none / **Accept selected** / **Reject all**.
4. Reject leaves the model untouched.

## Privacy

Telemetry is **off by default** (**Tools → Telemetry opt-in**). When on, only generic events (`app.start`, `calc.run`, `export.pdf`) are logged — never floor plans.
