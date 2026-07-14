# Calculation formulas & assumptions (Phase 11)

**Edition label:** `L.I. 2008 practice tables v2026.1`  
**Status:** Illustrative engineering heuristics for preliminary residential design in Ghana.  
**Not** a substitute for manufacturer data, full installation-method tables, or CEWP sign-off.

Codes listed below appear in `DesignReport.assumptions()` after **Tools → Recalculate Loads**.

---

## 1. Connected load (W)

| Rule | Formula / value | Code |
|------|-----------------|------|
| Catalogue power | \(P =\) component `powerW` when &gt; 0 | `LOAD_CATALOGUE_POWER_W` |
| Socket (single) | 180 W average demand | `SOCKET_ASSUMED_180W` |
| Socket (twin / 2G) | \(2 × 180\) W | `SOCKET_ASSUMED_180W` |
| LED luminaire | 12 W | `LED_DEFAULT_12W` |
| Fluorescent | 36 W | `FLUORESCENT_DEFAULT_36W` |
| Cooker / 45 A DP | 7000 W | `COOKER_DEFAULT_7000W` |
| Water heater | 3000 W | `WATER_HEATER_DEFAULT_3000W` |
| Air conditioner | 2500 W | `AC_DEFAULT_2500W` |
| Unknown fixed load | 100 W | `UNKNOWN_LOAD_100W` |
| Switches / infrastructure | 0 W | — |

Source: `LoadTables`.

## 2. Design current (A)

- Single-phase finals: \(I = P / V\) with \(V = 230\) V default (or project nominal).
- Three-phase 400 V AC/distribution specials: \(I = P / (\sqrt{3}\, V)\).
- Protective device current for cooker / WH / AC uses **connected** \(P/V\); lighting and sockets use **after-diversity** current.

Source: `CircuitBuilder`, `DiversityCalculator`.

## 3. Diversity

| Kind | Rule | Code |
|------|------|------|
| Lighting | 100% of first 1000 W + 50% of remainder | `DIVERSITY_LIGHTING_1000_THEN_0_5` |
| Socket multi-outlet | factor 0.4 on connected W | `DIVERSITY_SOCKET_MULTI_0_4` |
| Socket single outlet | factor 1.0 | — |
| Cooker | \(I_\mathrm{div} = 10 + 0.3\,(I_\mathrm{rated}-10)\) A (or 0.8×P if \(I ≤ 10\) A) | `DIVERSITY_COOKER_BASE_10A_PLUS_0_3` |
| WH / AC / other | 100% connected for diversity total | — |
| Overall | if circuit count &gt; 3, multiply sum by 0.9 | `DIVERSITY_OVERALL_0_9_GT_3_CIRCUITS` |

Total design current: \(I_\mathrm{total} = P_\mathrm{after, overall} / V\).

Source: `DiversityCalculator`.

## 4. Cable length (m)

| Mode | Formula | Code |
|------|---------|------|
| Geometry from DB | \(L = (\overline{d_\mathrm{Manhattan, mm}}/1000)·1.25 + 2\) | `LENGTH_FROM_DB_GEOMETRY` |
| Default (no DB) | socket 15 m · lighting 12 m · other 10 m | `LENGTH_DEFAULT_FALLBACK` |

Source: `CableLengthEstimator`.

## 5. Voltage drop & cable size

\[
V_d = \frac{(mV/A/m) · I · L}{1000},\quad
V_d\% = 100 · V_d / V_\mathrm{supply}
\]

- Limit: **5%** (`CABLE_VD_MAX_5_PERCENT`)
- Pick smallest catalogue CSA with \(I_z ≥ I_\mathrm{design}\) and \(V_d ≤ 5\%\) (`CABLE_SIZE_FROM_CATALOGUE`)
- Prefer twin & earth for lighting/socket when available

Source: `CableSizer`.

## 6. MCB selection

- Next standard rating from `{6, 10, 16, 20, 32, 40, 63}` A ≥ design current (`MCB_NEXT_STANDARD_RATING`)
- Socket with design I &gt; 20 A → prefer ≥ 32 A (`SOCKET_BREAKER_PREFER_32A_GT_20A`)

Source: `StandardsValidator`, `CalcEngine`.

## 7. Validation (illustrative)

Always stamped: `STANDARDS_HEURISTIC_LI2008`.

Examples:

- Main design current &gt; 40 A warning; &gt; 60 A / 100 A errors  
- Vd &gt; 5% warning/error  
- Lighting circuit &gt; 16 A → split  
- No RCD / no earth / no DB when load devices present  

Source: `StandardsValidator`.

## 8. Empty library

`NO_LIBRARY_WARNING` when catalogue is empty but devices are placed.

---

## CEWP note

These factors are educational / preliminary. A Competent Electrical Wiring Professional must refine diversity, installation method, grouping factors, and protective-device selection for real Ghana installations under L.I. 2008 and applicable Ghana Standards.
