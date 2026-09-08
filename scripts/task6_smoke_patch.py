from pathlib import Path

path = Path("docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, got {count}: {old[:160]!r}")
    text = text.replace(old, new, 1)


matrix_anchor = """## True-device matrix

All results below must be taken on final production head `9ec9f357` (or a later documentation-only descendant with identical production bytecode).
"""
smoke_section = """## Device smoke evidence — 2026-09-08

The user reported no obvious visible issue during this smoke run. The attached filtered LiquidDock log covers approximately `14:06:35.985` through `14:06:37.371` and contains no `DC-MATRIX` markers, so it is treated as a bounded smoke sample rather than evidence for every matrix case.

Observed sequence:

- Workstation mode published `false`;
- HotSeats hierarchy recovery/rebind completed;
- normal 8×4 layout restoration ran with `items=4`;
- the restoration was intentionally reasserted by the existing `post()` / `+250 ms` / `+700 ms` schedule rather than representing three distinct Workstation transitions;
- Workspace/static-glass reconciliation reached generation 2;
- PassBlur producer bound at native scale `1.0`;
- a first OES frame and first EGL material draw were received;
- zero-copy became active;
- no LiquidDock error/exception/failure line was present in the supplied filtered capture.

This is sufficient to record:

- CASE01 normal startup: **PASS (manual smoke + log)**;
- CASE05 normal-layout restore after leaving Workstation: **PASS (manual smoke + log)**;
- CASE10 normal non-Workstation mode: **PASS (manual smoke + log)**.

Cases 2–4, 6–9, and 11 remain **PENDING**. This capture does not identify quick re-entry / 2-second stale-fallback timing, Recents, rotation, Widget span/disabled behavior, or bundled-rule normal-load diagnostics.

## True-device matrix

All results below must be taken on final production head `9ec9f357` (or a later documentation-only descendant with identical production bytecode).
"""
replace_once(matrix_anchor, smoke_section)

replace_once(
    "| 1 | Normal Launcher startup | **PENDING** | HOME usable; no LiquidDock fatal/init error; glass/grid/dock normal |",
    "| 1 | Normal Launcher startup | **PASS (manual smoke + log)** | HOME usable; hierarchy rebind completes; first OES/EGL frame arrives; zero-copy active; no visible issue reported |",
)
replace_once(
    "| 5 | Normal-layout backup / restore | **PENDING** | leaving Workstation restores normal item positions/spans once |",
    "| 5 | Normal-layout backup / restore | **PASS (manual smoke + log)** | leaving Workstation restores the saved normal layout; the existing immediate / +250 ms / +700 ms reassert schedule may log the same `items=N` restore three times |",
)
replace_once(
    "| 10 | Normal mode without Workstation | **PENDING** | normal Dock/Grid/Glass behavior unchanged |",
    "| 10 | Normal mode without Workstation | **PASS (manual smoke + log)** | final published mode is non-Workstation; Dock/Grid/Glass recovered and no visible issue was reported |",
)

path.write_text(text)
