from pathlib import Path

verification = Path("docs/superpowers/verification/2026-09-07-technical-debt-cleanup-phase1.md")
todo = Path("TODO.md")

v = verification.read_text()
t = todo.read_text()


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}: {old[:160]!r}")
    return text.replace(old, new, 1)

v = replace_once(
    v,
    "**PHASE_1_DEVICE_GATE_PENDING**",
    "**PHASE_1_PASS_USER_ACCEPTED**",
    "verification status",
)
v = replace_once(
    v,
    "Tasks 1–5 have code/CI evidence. Phase 1 is not complete until the final-head true-device matrix below is recorded. No EGL/OES/producer lifecycle extraction may begin while this gate is pending.",
    "Tasks 1–5 have code/CI evidence. On 2026-09-08 the user explicitly accepted all remaining true-device cases as PASS. Phase 1 is therefore closed at 11/11 device cases. Evidence strength remains explicit below: CASE01/02/03/05/06/10 have smoke/marker-log support, while CASE04/07/08/09/11 are user manual-acceptance PASS without per-case marker/log proof. Subsequent EGL/OES/producer lifecycle work may begin as a new phase without retroactively upgrading those manual acceptances into log-backed evidence.",
    "verification status detail",
)

v = replace_once(v, "- Device: **PENDING**", "- Device: **NOT_RECORDED (user-accepted closure)**", "device metadata")
v = replace_once(v, "- ROM / HyperOS build: **PENDING**", "- ROM / HyperOS build: **NOT_RECORDED (user-accepted closure)**", "rom metadata")
v = replace_once(v, "- MIUI Home / Launcher version: **PENDING**", "- MIUI Home / Launcher version: **NOT_RECORDED (user-accepted closure)**", "launcher metadata")
v = replace_once(v, "- LSPosed version: **PENDING**", "- LSPosed version: **NOT_RECORDED (user-accepted closure)**", "lsposed metadata")

v = replace_once(
    v,
    "- CASE04 stale delayed fallback: **PENDING**. The final `Mingou workstation mode changed=true` is only about 1.5 seconds before the CASE04 END marker, so this capture does not observe past the required 2-second stale-fallback deadline after the final re-entry.",
    "- CASE04 stale delayed fallback: **PASS (user manual acceptance)**. The available marker log only observes about 1.5 seconds after the final `true`, so this row is not log-proven across the full 2-second deadline; the user explicitly accepted it as PASS.",
    "case04 evidence",
)
v = replace_once(
    v,
    "- CASE07 Recents-adjacent rotation: **PENDING**. No CASE07 marker or identifiable rotation sequence is present in the supplied file.",
    "- CASE07 Recents-adjacent rotation: **PASS (user manual acceptance)**. No CASE07 marker or identifiable rotation sequence is present in the supplied file; the user explicitly accepted it as PASS.",
    "case07 evidence",
)
v = replace_once(
    v,
    "The device gate therefore advances from 3/11 to **6/11**. Remaining cases are 4, 7, 8, 9, and 11.",
    "The marker-backed review advanced the device gate from 3/11 to 6/11. The user then explicitly accepted CASE04, CASE07, CASE08, CASE09, and CASE11 as PASS, closing the device matrix at **11/11**. Those five rows remain labeled manual acceptance rather than log-backed proof.",
    "device gate summary",
)

rows = {
    "| 4 | Stale delayed fallback after a newer transition | **PENDING** | no later stale mode flip/restore after the newer transition |":
    "| 4 | Stale delayed fallback after a newer transition | **PASS (user manual acceptance)** | accepted by user; available marker log did not extend beyond the full 2 s post-final-reentry window |",
    "| 7 | Recents-adjacent rotation | **PENDING** | correct orientation geometry and fresh glass after settle |":
    "| 7 | Recents-adjacent rotation | **PASS (user manual acceptance)** | accepted by user; no dedicated CASE07 marker/rotation sequence was captured |",
    "| 8 | 1×1 / 2×1 / 2×2 / 4×2 Widgets, portrait + landscape | **PENDING** | all supported spans tile/size correctly; no placement/occupancy regression |":
    "| 8 | 1×1 / 2×1 / 2×2 / 4×2 Widgets, portrait + landscape | **PASS (user manual acceptance)** | accepted by user; no per-span marker/log evidence was supplied |",
    "| 9 | Widget adaptation disabled | **PENDING** | MIUI native Widget frame/layout remains untouched |":
    "| 9 | Widget adaptation disabled | **PASS (user manual acceptance)** | accepted by user; no dedicated disabled-mode marker/log evidence was supplied |",
    "| 11 | Normal bundled Widget-rule load | **PENDING** | no `[DC][WidgetRules] bundled_rules_unavailable` warning |":
    "| 11 | Normal bundled Widget-rule load | **PASS (user manual acceptance)** | accepted by user; this is not upgraded to a log-proven absence claim |",
}
for old, new in rows.items():
    v = replace_once(v, old, new, old[:40])

v = replace_once(
    v,
    "## Closure rule\n\nDo not change this document to `PASS`, mark Phase 1 complete in `TODO.md`, or begin Phase 2/3 GPU ownership extraction until cases 1–11 are recorded on the final production head and all failures are either fixed and reverified or explicitly block the phase.",
    "## Closure\n\n**Phase 1 closed on 2026-09-08 by explicit user acceptance of the full 11/11 device matrix.** Tasks 1–5 retain their code/CI evidence. Device evidence is mixed-strength and must remain described accurately: CASE01/02/03/05/06/10 have smoke/marker-log support; CASE04/07/08/09/11 are user manual-acceptance PASS without equivalent per-case log proof. Phase 2/3 work may begin, but later documentation must not rewrite manual acceptance as if it were captured diagnostic evidence.",
    "closure section",
)

old_todo_block = """**状态：Tasks 1–5 已完成代码实现并通过 CI；最终真机矩阵待完成。**"""
new_todo_block = """**状态：Phase 1 已完成；Tasks 1–5 已通过代码/CI 验证，最终真机矩阵按用户验收记为 11/11 PASS。**"""
t = replace_once(t, old_todo_block, new_todo_block, "todo phase1 status")

old_gate = """Phase 1 当前唯一关闭门是最终 production head 的真机矩阵。必须完成并记录：

- normal Launcher startup；
- Workstation enter / exit / quick re-enter；
- vendor callback 先于 delayed fallback，以及 stale delayed fallback；
- normal-layout backup/restore；
- HOME -> Recents -> HOME 连续 5 次；
- Recents-adjacent rotation；
- 1×1 / 2×1 / 2×2 / 4×2 Widget 横竖屏；
- Widget adaptation disabled；
- normal mode 无回归；
- normal bundled Widget-rule load 不出现 degradation warning。

在该矩阵完成前，不把 Phase 1 标记为完成，也不开始 EGL/OES/producer lifecycle extraction。"""
new_gate = """Phase 1 的 11 项设备矩阵已关闭：CASE01/02/03/05/06/10 有 smoke/marker-log 支持；CASE04/07/08/09/11 由用户明确手工验收为 PASS，但没有同等级的逐项 marker/log 证据。该证据强度差异保留在 verification record 中，不在后续文档中改写为“全部日志证明”。

Phase 1 现已完成。后续可以进入 `MainHook` 继续收缩以及 Launcher-wide glass / GPU ownership 审计，但仍必须遵守既有 zero-copy、fresh-frame、Recents authority 与 MIUI placement/occupancy 边界。"""
t = replace_once(t, old_gate, new_gate, "todo closure gate")

verification.write_text(v)
todo.write_text(t)
