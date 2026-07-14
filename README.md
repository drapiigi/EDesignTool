# GhanaWire AI (G-Wire Designer)

AI-assisted **desktop** application for designing household electrical wiring diagrams in compliance with:

- Ghana Electrical Wiring Regulations 2011 (**L.I. 2008**)
- Energy Commission guidelines
- Ghana Standards

Engineers and CEWPs can open a floor plan, auto-generate a design with AI, edit interactively, calculate loads, and export PDF / Excel BOQ.

| | |
|---|---|
| **Repo** | https://github.com/drapiigi/EDesignTool |
| **Stack** | Java 21+, JavaFX 23+, Maven, H2, PDFBox, Apache POI |
| **Status** | **Beta 0.9.0** + Phases 13a–15 (CAD, electrical model 1.3, templates/price book/AI preview) · next: Phase 16 GA |

See **[AGENTS.md](AGENTS.md)** for architecture and agent workflow.  
See **[docs/PACKAGING.md](docs/PACKAGING.md)** for installers and fat-jar notes.

---

## Requirements

- **JDK 21+**
- **Maven 3.9+**
- Display for JavaFX (`DISPLAY` on Linux)

```bash
export PATH="$HOME/.local/bin:$PATH"
mvn -version
```

---

## Quick start

```bash
cd /path/to/EDesignTool
export PATH="$HOME/.local/bin:$PATH"

mvn test
mvn javafx:run
```

Then: **Help → Open Sample 3-Bed House** to load a furnished Ghana bungalow demo.

---

## Features (Phases 1–8)

- Floor plan canvas (mm, grid snap, pan/zoom)
- Walls, rooms, doors, windows · image/PDF import
- Symbol library (~73 Ghana starter components, H2)
- Drag-and-drop place · drag devices to move
- Load calc, diversity, cable sizing, voltage drop, standards checks
- AI design (offline rules + optional LLM) · vision floor-plan analysis
- Co-pilot chat for simple edits
- **Save/Open** `.gwire` projects
- **PDF report** (plan, schedule, BOQ, checklist)
- **BOQ Excel** (`.xlsx`) export
- **Sample 3-bed house** · packaging scripts (jpackage / portable tarball)
- **Multi-storey** buildings · **wiring routes** on canvas · **single-line diagram**

### Common actions

| Action | How |
|--------|-----|
| Sample project | **Help → Open Sample 3-Bed House** |
| Save / Open | **File → Save** (Ctrl+S) · **Open** (Ctrl+O) |
| Export PDF | **File → Export PDF Report** (Ctrl+E) |
| Export BOQ Excel | **File → Export BOQ (Excel)…** |
| AI design | **Design → AI Generate Design** (Ctrl+G) |
| Vision rooms | Import plan → **Design → Analyze Floor Plan (Vision)** |
| Recalculate | **Tools → Recalculate Loads** (Ctrl+R) |
| Validate | **Tools → Validate Standards** (Ctrl+L) |
| Wiring routes | **Tools → Generate Wiring Routes** · View → Show wiring routes |
| SLD | **View → Single-Line Diagram…** (also in PDF) |
| Storeys | Storey bar under drawing tools · **+ Floor** |

### Optional LLM / vision

```bash
export GWIRE_AI_PROVIDER=openai   # or xai
export GWIRE_AI_API_KEY=...
export GWIRE_AI_MODEL=gpt-4o-mini
```

Or `~/.gwire/ai.properties`. Without a key, offline rules and a simple vision fallback still work.

Never commit API keys. Component library: `~/.gwire/library`.

> Calculations and AI heuristics are for **preliminary design**. A **CEWP** must verify real installations.

---

## Packaging

```bash
# Linux app-image
./scripts/package-linux.sh

# Windows (PowerShell)
powershell -File scripts/package-windows.ps1
```

Details: [docs/PACKAGING.md](docs/PACKAGING.md).

---

## Roadmap

1. **Phase 1** — Maven + JavaFX shell ✅  
2. **Phase 2** — Floor plan drawing + import ✅  
3. **Phase 3** — Symbol library + starter component DB ✅  
4. **Phase 4** — Calculation & standards engine ✅  
5. **Phase 5** — AI design + vision ✅  
6. **Phase 6** — Project save/load ✅  
7. **Phase 7** — PDF + Excel BOQ export ✅  
8. **Phase 8** — Packaging, sample 3-bed house, polish ✅  
9. **Phase 9** — Multi-storey, wiring routes, SLD, packaging polish ✅  
10. **Phase 10** — Atomic save, autosave/recovery, secrets store, exception handler, `.gwirez` package ✅  
11. **Phase 11** — Assumption flags, golden calcs, export gates, FORMULAS.md ✅  
12. **Phase 12** — Beta 0.9: installers, first-run disclaimer, update check, LICENSE ✅  
13a. **Phase 13a** — Ortho, endpoint OSNAP, layers, storey-safe undo, PDF plot scale ✅  
13b–16. **Planned** — CAD stretch / electrical model / GA — see **[docs/ROADMAP-PRODUCTION.md](docs/ROADMAP-PRODUCTION.md)**  

---

## License

TBD.
