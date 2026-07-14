# GhanaWire AI 1.0.0 — GA release checklist

## Product

- [x] Phase 10–12: durability, calc integrity, packaging beta
- [x] Phase 13a: ortho, OSNAP, layers, storey undo, plot scale
- [x] Phase 13b: grips, linear dimensions, DXF import/export MVP
- [x] Phase 14: circuits, CU, format 1.3+
- [x] Phase 15: templates, scale calibrate, CAD cmd, price book, AI preview, telemetry
- [x] UI: tabbed dock + dedicated AI Chat
- [x] Phase 16: spatial index, architecture doc, semver **1.0.0**

## Engineering

- [x] `mvn test` green on main
- [x] Format load 1.0–1.4
- [x] Golden calc harness
- [x] CI example: `docs/ci/package.yml.example` (copy to `.github/workflows/` when token has `workflow` scope)
- [ ] Signed installers (optional; document unsigned if not available)
- [ ] CEWP peer review of load tables (external)

## Legal / claims

- [x] LICENSE present
- [x] First-run CEWP disclaimer
- [x] “Preliminary design aid only” on PDF / About
- [ ] Final legal review (owner)

## Release steps

1. Tag `v1.0.0`
2. Run `./scripts/package-linux.sh` (and Windows script if available)
3. Attach artifacts to GitHub Release
4. Confirm `docs/release/version.json` points to `1.0.0`
5. Smoke: open sample, recalculate, export PDF, AI Chat, DXF round-trip

## Explicit non-goals (post-1.0)

Full AutoCAD clone, 3D BIM, industrial plant design, cloud multi-user.
