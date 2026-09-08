# OS4 Bloom Overlay Milestone 2 Verification

## Scope

Branch: `port/os4-volumetric-edge-model`
PR: #139
Design: `docs/superpowers/specs/2026-09-08-os4-bloom-overlay-design.md`
Plan: `docs/superpowers/plans/2026-09-08-os4-bloom-overlay-milestone2.md`

## Task 1 — formal OS4 parameters and glass-pass uniforms

RED:
- CI #4364 on `a118bb2b9986a102c18594685f16341c9ac14c79`
- expected test-compile failure because the seven OS4 `PrismalParams` fields did not exist.

Implementation staged:
- `b7fb53213e263309be660caaef26f9f03848895b` — expose OS4 params.
- `66515d8d9251d2d8444a1f3afdd760412600959c` — drive OS4 shader from uniforms.
- `86c260049e8ad87e4927e95bd83b6df37072d35b` — bind renderer uniforms; one-shot helper/workflow self-deleted.

CI #4369 on the bot-authored renderer patch is `action_required` with no executed test job, so it is neither a pass nor a test failure.

Current gate: `TASK_1_GREEN_PENDING`
