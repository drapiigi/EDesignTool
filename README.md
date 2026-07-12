# GhanaWire AI (G-Wire Designer)

AI-assisted **desktop** application for designing household electrical wiring diagrams in compliance with:

- Ghana Electrical Wiring Regulations 2011 (**L.I. 2008**)
- Energy Commission guidelines
- Ghana Standards

Engineers and CEWPs can open a floor plan, auto-generate a compliant design with AI, then edit interactively with live calculations and BOQ.

| | |
|---|---|
| **Repo** | https://github.com/drapiigi/EDesignTool |
| **Stack** | Java 21+, JavaFX 23+, Maven, H2, Apache PDFBox |
| **Status** | Phase 4 — calculation & standards engine |

See **[AGENTS.md](AGENTS.md)** for architecture, phases, and agent workflow.

---

## Requirements

- **JDK 21+** (developed/tested with OpenJDK 25)
- **Maven 3.9+**
- Display available for JavaFX (`DISPLAY` on Linux)

### Install Maven (user-local, if needed)

```bash
# example if mvn is not on PATH
export PATH="$HOME/.local/bin:$PATH"
mvn -version
```

---

## Build & run

```bash
cd /home/drapiigi/Projects/EDesignTool   # or your clone path

# Compile + unit tests
mvn test

# Run the desktop UI
mvn javafx:run
```

### Package (fat jar — Phase 1)

```bash
mvn package
# artifact: target/gwire-designer-0.1.0-SNAPSHOT.jar
```

JavaFX native libraries must still be available at runtime for the shaded jar on some platforms; prefer `mvn javafx:run` during development. Installers via **jpackage** land in Phase 8.

---

## Features (through Phase 4)

- Main window with dark/light themes, menus, status bar
- Floor plan canvas (mm world units, 500 mm grid snap)
- Tools: Select, Pan, Wall, Room, Door, Window
- Import floor plan background: images + PDF (page 1)
- Undo/redo, delete selection, zoom / fit view
- Project properties (name, house type, 230 V / 400 V supply)
- **Symbol library** (H2 catalogue, ~73 Ghana starter components)
- Drag-and-drop place · drag placed symbols to move
- **Load calc, diversity, cable sizing, voltage drop**
- **Standards validation** (illustrative L.I. 2008 practice checks)
- BOQ: devices + estimated circuit cable lengths (GHS)

### Canvas tips

| Action | How |
|--------|-----|
| Draw wall | Tool **Wall** → click start → click end |
| Draw room | Tool **Room** → drag rectangle |
| Door / window | Tool **Door**/**Window** → click near a wall |
| Pan | Tool **Pan**, or middle-mouse drag |
| Zoom | Mouse wheel · View menu |
| Delete | Select element → Delete |
| Import plan | Toolbar **Import plan…** or File → Import Floor Plan |
| Place symbol | **Drag** from symbol library onto the canvas |
| Move symbol | **Select** tool → drag a placed device to a new position |
| Recalculate | **Tools → Recalculate Loads** (Ctrl+R) |
| Validate | **Tools → Validate Standards** (Ctrl+L) |

Component library DB (auto-seeded): `~/.gwire/library`

> Calculation parameters are simplified heuristics for preliminary design. A CEWP must verify real installations.

---

## Roadmap

1. **Phase 1** — Maven + JavaFX shell ✅  
2. **Phase 2** — Floor plan drawing + import ✅  
3. **Phase 3** — Symbol library + starter component DB ✅  
4. **Phase 4** — Calculation & standards engine ✅  
5. **Phase 5** — AI design generation  
6. **Phase 6** — Real-time updates, BOQ, validation, save/load  
7. **Phase 7** — PDF export & reports  
8. **Phase 8** — Packaging, sample project, polish  

---

## License

TBD.
