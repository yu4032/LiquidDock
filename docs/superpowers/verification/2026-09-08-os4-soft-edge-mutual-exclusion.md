# OS4 Soft Edge Mutual-Exclusion Verification

## Approved behavior

- `OS4 柔光边缘` defaults ON.
- ON: show the seven OS4 controls; disable the legacy highlight-components entry/page; force the nine legacy Prismal highlight components OFF at runtime; keep OS4 background reflection and OS4 directional soft light active.
- OFF: hide the seven OS4 controls; disable OS4 volumetric/reflection/directional contribution through the independent shader mode gate; restore the user's saved legacy nine-component profile unchanged.
- No PassBlur/OES/freshness/Launcher lifecycle ownership changes.

## TDD evidence

- API101 #4401: intended RED; mode schema/policy/runtime fields did not exist.
- One-shot Task 1 run `34216285475`: focused mutual-exclusion policy PASS, full `testDebugUnitTest assembleDebug` PASS, production patch committed and temporary workflow/scripts removed.
- API101 #4406: intended RED; exactly two new Prismal shader-mode contracts failed while the other 25 Prismal tests passed.
- API101 #4407: intended RED; portable `PrismalParams.os4SoftEdgeEnabled` was absent, producing the expected two app-test compile errors.
- One-shot Task 2 run `34217126066`: runtime mode propagation PASS, Prismal shader-gate contracts PASS, full `testDebugUnitTest assembleDebug` PASS, production patch committed and temporary workflow/script removed.

## Production boundary from CI #4400 head

Production/test changes are limited to the OS4/legacy mode policy, Compose visibility/disable behavior, configuration propagation, Prismal mode uniform/gating, removal of the temporary legacy Lit/Opposite Rim injection, and focused tests. No standalone Bloom FBO path is introduced.

## Final gate

A normal-user docs-only descendant triggers the standard API101 PR workflow. Device visual behavior remains pending until the exact final CI APK is tested on target hardware.
