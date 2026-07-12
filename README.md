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
| **Status** | Phase 1 — application shell |

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

## Phase 1 features

- Main window with professional dark/light themes
- Menu bar (File, Edit, View, Design, Tools, Help)
- Canvas placeholder (floor plan arrives Phase 2)
- Properties + BOQ side panels
- Status bar with L.I. 2008 / 230 V context
- About dialog

---

## Roadmap

1. **Phase 1** — Maven + JavaFX shell ✅  
2. **Phase 2** — Floor plan drawing + import  
3. **Phase 3** — Symbol library + starter component DB  
4. **Phase 4** — Calculation & standards engine  
5. **Phase 5** — AI design generation  
6. **Phase 6** — Real-time updates, BOQ, validation  
7. **Phase 7** — PDF export & reports  
8. **Phase 8** — Packaging, sample project, polish  

---

## License

TBD.
