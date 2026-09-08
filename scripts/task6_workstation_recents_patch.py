from pathlib import Path

path = Path("docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, got {count}: {old[:160]!r}")
    text = text.replace(old, new, 1)

anchor = """Cases 2–4, 6–9, and 11 remain **PENDING**. This capture does not identify quick re-entry / 2-second stale-fallback timing, Recents, rotation, Widget span/disabled behavior, or bundled-rule normal-load diagnostics.

## True-device matrix
"""
replacement = """Cases 2–4, 6–9, and 11 remain **PENDING**. This capture does not identify quick re-entry / 2-second stale-fallback timing, Recents, rotation, Widget span/disabled behavior, or bundled-rule normal-load diagnostics.

## Workstation / Recents evidence — 2026-09-08

The user reported no visible abnormality while exercising the Workstation/Recents batch. The attached marker-based log supports the following bounded conclusions:

- CASE02 quick Workstation re-entry: **PASS**. The published mode sequence is `true -> false -> true`; the final `true` remains authoritative through the case end without a later stale reversal.
- CASE03 vendor-before-fallback stability: **PASS**. In the final marked interval, Workstation publishes `true` and remains in that state for more than the 2-second fallback window until case end. The later `false` occurs after CASE03 ended, during the subsequent transition.
- CASE06 HOME -> Recents -> HOME x5: **PASS**. The log contains exactly five `recents-wallpaper` pending generations `5/7/9/11/13` and five matching settled generations `6/8/10/12/14`. No producer bind/unbind churn is present inside that interval, and the user observed no stale/frozen glass.
- CASE04 stale delayed fallback: **PENDING**. The final `Mingou workstation mode changed=true` is only about 1.5 seconds before the CASE04 END marker, so this capture does not observe past the required 2-second stale-fallback deadline after the final re-entry.
- CASE07 Recents-adjacent rotation: **PENDING**. No CASE07 marker or identifiable rotation sequence is present in the supplied file.

The device gate therefore advances from 3/11 to **6/11**. Remaining cases are 4, 7, 8, 9, and 11.

## True-device matrix
"""
replace_once(anchor, replacement)

replace_once(
    "| 2 | Workstation enter → exit → quick re-enter | **PENDING** | final mode matches UI; no stale restore or duplicate transition |",
    "| 2 | Workstation enter → exit → quick re-enter | **PASS (manual + marker log)** | published sequence `true -> false -> true`; final `true` persists through case end with no stale reversal |",
)
replace_once(
    "| 3 | Vendor Workstation callback before delayed fallback | **PENDING** | vendor-confirmed state remains authoritative after the 2 s window |",
    "| 3 | Vendor Workstation callback before delayed fallback | **PASS (manual + marker log)** | final Workstation `true` remains stable beyond the 2 s fallback window until case end; no later reversal occurs inside the case |",
)
replace_once(
    "| 6 | HOME → Recents → HOME ×5 | **PENDING** | no stale glass, producer loss, repeated endpoint rebuild, or frozen source |",
    "| 6 | HOME → Recents → HOME ×5 | **PASS (manual + marker log)** | five pending/settled generation pairs complete; no producer bind/unbind churn in the case; no stale/frozen glass reported |",
)

path.write_text(text)
