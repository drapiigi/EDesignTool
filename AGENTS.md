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
| 5 | AI generation integration | Planned |
| 6 | Real-time updates, BOQ, validation | Planned |
| 7 | PDF export & reporting | Planned |
| 8 | Packaging, polish, sample 3-bed house, docs | Planned |

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

### Open tasks

- [ ] Phase 5: AI generate design (vision + LLM + rule fallback)
- [ ] Phase 6: project save/load, richer live model updates
- [ ] Phase 7: PDF exports (plans, SLD, schedules, BOQ, checklist)
- [ ] Phase 8: jpackage installers, sample project, full docs

---

## Key paths

```
src/main/java/com/ghana/gwire/
  Main.java, GWireApp.java
  domain/geometry/, domain/floorplan/, domain/project/, domain/components/, domain/calc/
  db/ (H2 library, seed, repository)
  service/importing/, service/history/, service/calc/
  ui/MainWindow.java, ui/menu/, ui/panels/, ui/theme/, ui/canvas/, ui/symbols/
src/main/resources/css/
src/test/java/
pom.xml
AGENTS.md
README.md
```
