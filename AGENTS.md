# GhanaWire AI (G-Wire Designer) — Agents & Architecture

**Repo:** [drapiigi/EDesignTool](https://github.com/drapiigi/EDesignTool)  
**Local path:** `/home/drapiigi/Projects/EDesignTool`  
**Product:** AI-assisted desktop electrical wiring design for Ghana (L.I. 2008 / Energy Commission / Ghana Standards)

---

## Overall system architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     GWireApp (JavaFX shell)                      │
│  MenuBar · Theme · StatusBar · MainWindow layout                 │
└───────────────┬─────────────────────────┬───────────────────────┘
                │                         │
    ┌───────────▼──────────┐   ┌──────────▼──────────┐
    │  Floor Plan Canvas   │   │  Side panels         │
    │  (vector + image)    │   │  Properties · BOQ    │
    └───────────┬──────────┘   └──────────┬──────────┘
                │                         │
    ┌───────────▼─────────────────────────▼──────────┐
    │              Domain / Services                  │
    │  ProjectModel · SymbolLibrary · CalcEngine      │
    │  StandardsValidator (L.I. 2008) · BoqService    │
    │  AiDesignService · ExportService (PDF)          │
    └───────────┬─────────────────────────┬──────────┘
                │                         │
         ┌──────▼──────┐           ┌──────▼──────┐
         │  H2 embed   │           │  LLM / Vision│
         │  components │           │  REST APIs   │
         │  projects   │           │  + rule FB   │
         └─────────────┘           └─────────────┘
```

### Layering rules

| Layer | Package (planned) | Responsibility |
|-------|-------------------|----------------|
| UI | `com.ghana.gwire.ui.*` | JavaFX views, controllers, CSS themes |
| Domain | `com.ghana.gwire.domain.*` | Rooms, circuits, devices, cables, project graph |
| Services | `com.ghana.gwire.service.*` | Calculations, standards, BOQ, AI, export |
| Persistence | `com.ghana.gwire.db.*` | H2 schema, component seed, project save/load |
| AI | `com.ghana.gwire.ai.*` | Provider clients, prompts, vision pipeline, fallbacks |

**Standards context:** Ghana Electrical Wiring Regulations 2011 (**L.I. 2008**), Energy Commission guidelines, Ghana Standards; default supply **230 V / 50 Hz**.

---

## Phased delivery plan

| Phase | Name | Status |
|-------|------|--------|
| 1 | Maven + JavaFX shell, menus, themes, layout placeholders | **Done** |
| 2 | Floor plan module (draw + image/PDF import) | **Done** |
| 3 | Electrical symbol library + starter component DB | **Done** |
| 4 | Calculation & standards engine | **Done** |
| 5 | AI generation integration | **Done** (rules + optional LLM + co-pilot + vision) |
| 6 | Project save/load + live model updates | **Done** |
| 7 | PDF export & reporting | **Done** |
| 8 | Packaging, polish, sample 3-bed house, docs | **Done** |
| 9 | Multi-storey, wiring routes, SLD, packaging polish | **Done** |
| 10 | Production hardening: atomic save, autosave/recovery, SecretStore, exception handler, smoke tests | **Done** |
| 11 | Calculation integrity: golden tests, assumption flags, calc state machine, export gates | **Done** |
| 12 | Distribution + liability UX → **beta 0.9** (installers, disclaimers, standards stamp) | **Done** |
| 13a | CAD minimum for GA (OSNAP/ortho, layers, command undo, basic plot) | **Done** |
| 13b | CAD stretch (DXF, full dims, grips) | **Stretch** |
| 14 | Electrical model depth (circuits, CU board, cable schedule, SLD model) | **Planned** |
| 15 | Product maturity (scale cal, templates, price book, AI diffs, manuals) | **Planned** |
| 16 | Scale architecture + **GA 1.0** (semver, CI packages, spatial index) | **Planned** |

**Production roadmap (detail):** [docs/ROADMAP-PRODUCTION.md](docs/ROADMAP-PRODUCTION.md)  
**Critical path:** 10 → 11 → 12 (beta) → **13a ∥ 14** → 15 → 16 (GA). Parallel tracks: legal copy, CEWP table review, license, signing keys.

---

## Orchestration model (Grok Build)

### Parent (orchestrator)

- Owns product goal, architecture decisions, phase sequencing, and git hygiene.
- Spawns subagents for exploration, implementation slices, and review.
- Updates this file after each phase.
- Asks the user before destructive git operations or ambiguous product decisions.

### Subagents (roles)

| Role | Subagent type | Responsibilities | Tech focus |
|------|---------------|------------------|------------|
| **Explorer** | `explore` | Codebase search, dependency audit, locate extension points | Read-only |
| **Planner** | `plan` | Implementation plans for a phase/PR | Read-only |
| **Implementer** | `general-purpose` | Feature slices, tests, UI work | Java 21, JavaFX, Maven |
| **Reviewer** | `general-purpose` | Diff review, standards comments, test gaps | Quality gate |
| **Designer (docs)** | `/design` skill | Architecture design docs + PR plans | Markdown |

### Communication patterns

1. **Parent → child:** full task prompt with paths, constraints, success criteria, and phase context from this file.
2. **Child → parent:** summary of files changed, how to run, open risks.
3. **Parallel work:** `isolation: worktree` when two implementers touch different areas.
4. **No nested spawn:** only the parent spawns subagents (Grok depth limit = 1).
5. **Resume:** multi-step work uses `resume_from` on the same child when context must carry over.

### How to spawn / interact (for humans)

- **In Grok TUI:** state the goal and ask to implement the next phase; parent will spawn agents as needed.
- **View tasks:** `Ctrl+B` (tasks / subagents pane).
- **Goal mode (if enabled):** `/goal <objective>` then continue the conversation.
- **Design loop:** `/design <feature>` then implement from the PR plan.
- **Local run (Phase 1):**
  ```bash
  cd /home/drapiigi/Projects/EDesignTool
  mvn -q test
  mvn javafx:run
  ```

---

## Coding conventions

- Java **21** language level; JavaFX **23+**; Maven.
- Package root: `com.ghana.gwire`.
- Prefer clear domain names over abbreviations in public APIs.
- Comment Ghana regulatory references where rules are encoded (L.I. 2008, EC guidance).
- Embedded H2 for components/projects; API keys via config/env, never committed.
- Ask before force-push, hard reset, or dropping data.

---

## Current implementation status

### Done (Phase 1)

- Maven project (`gwire-designer` 0.1.0-SNAPSHOT)
- Dependencies: JavaFX 23.0.2, H2, PDFBox, Jackson, SLF4J/Logback, JUnit 5
- Application shell: `Main`, `GWireApp`, `MainWindow`
- Menus: File / Edit / View / Design / Tools / Help
- Dark/light themes (`theme-dark.css`, `theme-light.css`)

### Done (Phase 2)

- Domain model: `FloorPlan`, `Wall`, `Room`, `Opening`, `BackgroundImage`, `Project`, `ProjectSettings`
- Geometry helpers: `Vec2`, `Segment2` (hit-test, snap)
- Canvas: pan/zoom, grid (500 mm snap), tools Select / Pan / Wall / Room / Door / Window
- Import: PNG/JPEG/GIF/BMP/WebP + PDF page 1 via PDFBox → background raster
- Undo/redo stack for geometry (`FloorPlanHistory`)
- Properties panel: project name, house type, supply, selection details, room rename
- Unit tests: geometry, floor plan, history, import

### Done (Phase 3)

- H2 component library at `~/.gwire/library` with Ghana starter seed (**73** items)
- Domain: `ElectricalComponent`, `ComponentCategory`, `PlacedDevice`
- `ComponentLibraryService` / repository / seed / `LibraryBootstrap`
- Symbol library panel (filter, search; **drag-and-drop** onto canvas)
- `SymbolRenderer` (IEC/BS-inspired glyphs by `symbolKey`)
- Canvas: drop to place · **drag placed devices to move** (live, grid snap, undo)
- BOQ panel counts placed devices with GHS costs from catalogue

### Done (Phase 4)

- Load estimation (`LoadTables`), diversity (`DiversityCalculator`)
- Circuit grouping (`CircuitBuilder`), cable length estimate, cable sizer (CSA + Vd)
- `StandardsValidator` (NO_RCD, NO_EARTH, NO_DB, VD_EXCEEDED, OVERLOAD_MAIN, …)
- `CalcEngine` → `DesignReport` stored on `Project.lastReport`
- UI: Tools → Recalculate Loads / Validate Standards; calc results panel; BOQ cable lengths

### Done (Phase 5)

- Package `com.ghana.gwire.ai`: `AiSettings`, `DesignPlacement`, `AiDesignPlan`, `RuleBasedDesignGenerator`, `OpenAiCompatClient`, `LlmDesignGenerator`, `AiDesignService`, `AiCopilotResult`
- Rule-based Ghana residential heuristics (offline); optional OpenAI-compatible LLM; fallback to rules
- Apply plan → `PlacedDevice`s; simple co-pilot commands
- UI: Design → AI Generate Design / rules only; Tools → AI Co-pilot Chat
- Config: env (`GWIRE_AI_*`, `OPENAI_API_KEY`, `XAI_API_KEY`) and `~/.gwire/ai.properties` (never commit keys)
- **Vision floor-plan analysis** (`ai.vision.*`): encode/downscale image, multimodal chat, parse rooms/walls/openings (normalized coords → mm), apply geometry; offline single-room fallback; UI Design → Analyze Floor Plan (Vision) / Vision + AI Design

### Done (Phase 6)

- `.gwire` JSON project format (`ProjectStore`, format 1.0)
- File → Open / Save / Save As; dirty indicator in window title
- Persist rooms, walls, openings, devices, settings, background path
- Reload background raster on open; live BOQ refresh on model changes
- Stale calc report cleared when geometry/devices change

### Done (Phase 7)

- `PdfExportService` (PDFBox): multi-page report — cover, floor plan drawing, circuit schedule, BOQ, compliance checklist
- File → Export PDF Report (Ctrl+E); runs calc if needed
- Unit test loads generated PDF and checks page count
- `BoqExcelExportService` (Apache POI): BOQ-only `.xlsx` export — File → Export BOQ (Excel)

### Done (Phase 8)

- Sample 3-bed Ghana bungalow (`SampleProjectFactory`, `samples/ghana-3bed-house.gwire`)
- Help → Open Sample 3-Bed House
- Packaging scripts: `scripts/package-linux.sh`, `scripts/package-windows.ps1`
- Docs: `docs/PACKAGING.md`, README packaging section

### Done (Phase 9)

- Multi-storey: `BuildingStorey`, active floor switcher (`StoreyBar`), persist format 1.1
- Wiring routes: `WiringRoute` + Manhattan auto-route from DB to loads; canvas overlay; View toggle
- Single-line diagram: `SingleLineDiagramBuilder`, View preview, PDF SLD page
- Packaging polish: `package-appimage.sh`, signing notes in PACKAGING.md
- Calc/BOQ/standards aggregate devices across all storeys

### Done (Phase 10)

- Atomic save (`AtomicFileWriter`) + rolling `.bak`/`.bak2`
- Autosave (`~/.gwire/autosave/`), crash recovery, window close Save / Don’t save / Cancel
- Format **1.2** write; optional **`.gwirez`** package with embedded media
- SecretStore (`~/.gwire/secrets.properties` mode 0600) for AI keys
- Uncaught exception handlers + rolling file logs under `~/.gwire/logs`
- Clear undo history on storey switch; canvas redraw coalesce + device viewport cull
- Service smoke test: sample → calc → PDF
- Docs: `docs/persist/FORMAT.md`

### Done (Phase 11)

- Assumption codes on every calc (`AssumptionCollector` / `AssumptionCodes`)
- `DesignReport.assumptions()`, `standardsEdition`, `calculatedAtExport`
- `CalcSessionState` (NONE / FRESH / DIRTY_CLEARED / ERRORS_PRESENT) + export gates
- UI banner + assumptions list in calc panel; PDF stamps standards + assumptions
- Golden harness + `src/test/resources/goldens/expected/sample-3bed.json`
- `docs/calc/FORMULAS.md`

### Done (Phase 12) — beta 0.9

- Version **0.9.0**; `LICENSE` beta notice; `docs/USER_GUIDE.md`
- First-run disclaimer + CEWP checkbox + unsigned beta notice (`FirstRunDialog`)
- Update check (`UpdateCheckService` + `docs/release/version.json`)
- Packaging scripts: versioned artifacts, optional deb/MSI, WiX notes
- PDF/About standards stamp with app version
- CI example: `docs/ci/package.yml.example` (copy to `.github/workflows/` when token has workflow scope)

### Done (Phase 13a)

- Storey-aware undo/redo (`FloorPlanHistory` entries carry `storeyId`)
- Ortho (F8 / Shift / toolbar) and endpoint OSNAP (F3 / toolbar) for wall drawing
- Layers: Architecture vs Electrical (View menu)
- PDF plan page plot scale bar (1:50 / 1:100)
- HUD shows OSNAP/Ortho state

### Done (Phase 14)

- First-class `Circuit`, `ConsumerUnit`, `ChecklistReview`, `MountingHeights` (`domain/electrical/`)
- `PlacedDevice.circuitId` + `mountingHeightMm`; Project circuits / CU / checklist
- `CircuitMaterializer` — materialize on calc, rematerialize from plan, route remap
- `CalcEngine` prefers persistent circuits; writes sizing back onto model
- Format **1.3** save/load (`ProjectStore`)
- UI: `ElectricalPanel` (Circuits / CU board / Cables / Checklist); device height + circuit in Properties
- SLD ordered by CU ways; PDF checklist shows reviewed state

### Done (Phase 15)

- Scale calibration tool (`CALIBRATE_SCALE` → `BackgroundImage.mmPerPixel`)
- Project templates: 1-bed, 3-bed, 2-storey (`SampleProjectFactory` + File → New from Template)
- CAD command line MVP (`CadCommandParser` + StatusBar Cmd field: LINE / length / ORTHO / OSNAP)
- Editable price book dialog (H2 `updateCost`)
- AI ghost preview with multi-select Accept / Reject (`AiPreviewSession` — generators unchanged)
- Keyboard cheat sheet (`docs/KEYBOARD.md` + Help menu)
- Telemetry opt-in default OFF (`TelemetryService` — no floor plans)
- `SecretStore` interface + `FileSecretStore` + stub `KeyringSecretStore`

### Open tasks / next program

- **Next implement:** Phase 16 — scale architecture & GA 1.0 (or 13b CAD stretch)
- Parallel: CEWP peer review of load tables; code signing for production
- Explicit non-goals until post-1.0: full AutoCAD clone, 3D BIM, industrial plant design, cloud multi-user

---

## Key paths

```
src/main/java/com/ghana/gwire/
  Main.java, GWireApp.java
  domain/geometry/, domain/floorplan/, domain/project/, domain/components/, domain/calc/, domain/electrical/
  db/ (H2 library, seed, repository)
  ai/ (Phase 5 design generation)
  service/importing/, service/history/, service/calc/, service/electrical/, service/persist/, service/export/
  ui/MainWindow.java, ui/menu/, ui/panels/, ui/theme/, ui/canvas/, ui/symbols/
src/main/resources/css/
src/test/java/
pom.xml
AGENTS.md
README.md
```
