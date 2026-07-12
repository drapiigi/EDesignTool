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
| **Status** | Phase 6 — project save/load |

See **[AGENTS.md](AGENTS.md)** for architecture, phases, and agent workflow.

---

## Requirements

- **JDK 21+** (developed/tested with OpenJDK 25)
- **Maven 3.9+**
- Display available for JavaFX (`DISPLAY` on Linux)

### Install Maven (user-local, if needed)

```bash
export PATH="$HOME/.local/bin:$PATH"
mvn -version
```

---

## Build & run

```bash
cd /home/drapiigi/Projects/EDesignTool

mvn test
mvn javafx:run
```

### Package (fat jar)

```bash
mvn package
# artifact: target/gwire-designer-0.1.0-SNAPSHOT.jar
```

Prefer `mvn javafx:run` during development. Installers via **jpackage** land in Phase 8.

---

## Features (through Phase 6)

- Floor plan canvas (mm units, grid snap, pan/zoom)
- Walls, rooms, doors, windows · image/PDF background import
- Symbol library (~73 Ghana starter components in H2)
- Drag-and-drop place · drag devices to move
- Load calc, diversity, cable sizing, voltage drop, standards checks
- **AI Generate Design** (offline rules always; optional LLM)
- **Vision floor-plan analysis** (detect rooms from imported image/PDF; optional LLM vision)
- **AI Co-pilot** for simple natural-language edits
- BOQ (devices + estimated circuit cables)
- **Save/Open** projects as `.gwire` JSON (File menu)
- Live BOQ refresh when devices move or change

### Workflow tips

| Action | How |
|--------|-----|
| Draw room | Tool **Room** → drag rectangle |
| Place symbol | Drag from library onto canvas |
| Move symbol | **Select** → drag device |
| **AI design** | Draw rooms → **Design → AI Generate Design** (Ctrl+G) |
| **Vision rooms** | Import plan → **Design → Analyze Floor Plan (Vision)** |
| **Vision + design** | Import plan → **Design → Vision + AI Design (full)** |
| Rules only | **Design → AI Generate (rules only)** |
| Co-pilot | **Tools → AI Co-pilot Chat** (e.g. `add socket in Living`) |
| Save / Open | **File → Save** (Ctrl+S) · **Open** (Ctrl+O) · **Save As** |
| Recalculate | **Tools → Recalculate Loads** (Ctrl+R) |
| Validate | **Tools → Validate Standards** (Ctrl+L) |

### Optional LLM config

Offline **rule-based** generation works with no API key.

```bash
# Environment (examples)
export GWIRE_AI_PROVIDER=openai   # or xai | none
export GWIRE_AI_API_KEY=sk-...
export GWIRE_AI_MODEL=gpt-4o-mini   # vision-capable model recommended
# export GWIRE_AI_BASE_URL=https://api.x.ai/v1   # for xAI Grok

# Or file: ~/.gwire/ai.properties
# provider=openai
# apiKey=...
# model=gpt-4o-mini
# baseUrl=https://api.openai.com/v1
```

Without an API key, vision still runs an **offline fallback** (one room covering the plan) so you can continue with rules-based electrical placement.

Never commit API keys. Component library DB: `~/.gwire/library`.

> Calculation and AI heuristics are for **preliminary design**. A **CEWP** must verify real installations.

---

## Roadmap

1. **Phase 1** — Maven + JavaFX shell ✅  
2. **Phase 2** — Floor plan drawing + import ✅  
3. **Phase 3** — Symbol library + starter component DB ✅  
4. **Phase 4** — Calculation & standards engine ✅  
5. **Phase 5** — AI design generation ✅  
6. **Phase 6** — Project save/load, richer live updates ✅  
7. **Phase 7** — PDF export & reports  
8. **Phase 8** — Packaging, sample project, polish  

---

## License

TBD.
