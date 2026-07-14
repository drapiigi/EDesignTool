# GhanaWire AI — User quick start (beta 0.9)

**Version:** 0.9.0  
**Standards pack:** L.I. 2008 practice tables v2026.1  

This is a **preliminary design aid**. A Competent Electrical Wiring Professional (CEWP) must verify any real installation.

---

## Install

| Platform | Artifact |
|----------|----------|
| Linux | `GhanaWireAI-0.9.0-linux-x64/` (app-image) or `.tar.gz` |
| Windows | `GhanaWireAI-0.9.0-windows-x64/` (app-image); MSI if WiX was available |
| Developers | `mvn javafx:run` |

See [PACKAGING.md](PACKAGING.md) for build scripts.

On first launch, accept the disclaimer and CEWP acknowledgment checkbox.

---

## 10-minute workflow

1. **Help → Open Sample 3-Bed House** (or File → New / Open).
2. Explore the plan: pan with empty drag or Space+drag; zoom with Ctrl+scroll.
3. Drag devices from the **symbol library** onto rooms.
4. **Tools → Recalculate Loads** — review connected load, diversity, cables, issues, and **assumptions**.
5. **Tools → Validate Standards** for L.I. 2008 practice checks (illustrative).
6. **File → Export PDF Report** or **Export BOQ (Excel)**.
7. **File → Save** (`.gwire`) or **Save as Package** (`.gwirez` with embedded plan images).

---

## Tips

- Autosave runs every 5 minutes when dirty; crash recovery is offered on next launch.
- If the design changes after a calculation, the status shows **Calculations outdated**.
- Export with validation **errors** requires an explicit acknowledgment.
- AI design is optional (API key in local secrets store or env). Offline rules always work.
- **Help → About** shows standards stamp and whether the build is signed.

---

## Support

- Formulas: [calc/FORMULAS.md](calc/FORMULAS.md)  
- Project format: [persist/FORMAT.md](persist/FORMAT.md)  
- Releases: https://github.com/drapiigi/EDesignTool/releases  
