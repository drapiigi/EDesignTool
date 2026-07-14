# GhanaWire AI — User quick start (beta 0.9)

**Version:** 1.0.0  
**Standards pack:** L.I. 2008 practice tables v2026.1  

This is a **preliminary design aid**. A Competent Electrical Wiring Professional (CEWP) must verify any real installation.

---

## Install

| Platform | Artifact |
|----------|----------|
| Linux | `GhanaWireAI-1.0.0-linux-x64/` (app-image) or `.tar.gz` |
| Windows | `GhanaWireAI-1.0.0-windows-x64/` (app-image); MSI if WiX was available |
| Developers | `mvn javafx:run` |

See [PACKAGING.md](PACKAGING.md) for build scripts.

On first launch, accept the disclaimer and CEWP acknowledgment checkbox.

---

## 10-minute workflow

1. **File → New from Template** (1-bed / 3-bed / 2-storey) or **Help → Open Sample 3-Bed House**.
2. Explore the plan: pan with empty drag or Space+drag; zoom with Ctrl+scroll.
3. **Import** a scanned plan if needed, then **Tools → Calibrate background scale** (two points + real length).
4. Drag devices from the left **Symbols** list onto rooms — or open the right-hand **AI Chat** tab (Ctrl+K).
5. **Tools → Recalculate Loads** — open **Results** / **Circuits** tabs for loads, CU, and checklist.
6. **Design → AI Generate** shows ghost placements; Accept selected or use AI Chat quick actions.
7. **Tools → Price book…** to edit catalogue unit costs (GHS) used by BOQ.
8. **File → Export PDF Report** or **Export BOQ (Excel)**.
9. **File → Save** (`.gwire` format **1.4**) or **Save as Package** (`.gwirez`). Dim tool / DXF under File menu.

---

## Tips

- CAD command line at the bottom: `LINE`, `3500`, `3.5m`, `ORTHO ON` — see [KEYBOARD.md](KEYBOARD.md).
- Autosave runs every 5 minutes when dirty; crash recovery is offered on next launch.
- If the design changes after a calculation, the status shows **Calculations outdated**.
- Export with validation **errors** requires an explicit acknowledgment.
- **Rebuild circuits from plan** (Electrical panel) re-groups devices; it replaces the circuit list.
- AI design is optional (API key in local secrets store or env). Offline rules always work.
- Telemetry is **off by default** (Tools → Telemetry opt-in); never sends floor plans.
- **Help → About** shows standards stamp and whether the build is signed.

---

## Support

- Formulas: [calc/FORMULAS.md](calc/FORMULAS.md)  
- Project format: [persist/FORMAT.md](persist/FORMAT.md)  
- Releases: https://github.com/drapiigi/EDesignTool/releases  
