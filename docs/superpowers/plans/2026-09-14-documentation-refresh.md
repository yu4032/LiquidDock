# LiquidDock Documentation Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the public and contributor-facing documentation so it matches the current `main` implementation at version 2.4.1.

**Architecture:** Treat `ModuleMain`, `MainHook`, `ConfigSchema`, the Compose settings UI, current production Hook/session/policy classes, Gradle/R8 configuration, and CI as authoritative. Historical design plans remain archival context and must not override production code. Documentation changes should not modify runtime behavior.

**Tech Stack:** Android 15+/API 33 minimum, compile/target SDK 37, Java 17, Kotlin/Compose, libxposed API 101, MiuiX PassBlur, OES/GLES, Prismal, R8.

**Spec:** Current `main` source tree and production configuration are the specification.

## Global Constraints

- Keep the current Launcher baseline explicit: HyperOS 3.0.307+ / `com.miui.home` release-4.50.x.x.
- Document Security Center separately from Launcher support and do not imply unverified builds are supported.
- Keep the zero-copy rule explicit: no ScreenCapture, PixelCopy, bitmap readback, or CPU texture round-trip in the active glass pipeline.
- Distinguish typed project-owned APIs from vendor/framework reflection, including R8 cross-ClassLoader class-name hazards.
- Preserve existing historical changelog entries; only add an Unreleased/current-main summary when needed.
- Do not rewrite `docs/superpowers/plans/*` historical plans as if they were current architecture.

---

### Task 1: Inventory current production behavior

**Files:**
- Read: `src/main/java/com/hellovoid/liquiddock/ModuleMain.java`
- Read: `src/main/java/com/hellovoid/liquiddock/MainHook.java`
- Read: `src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java`
- Read: `src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt`
- Read: active Hook/session/policy/render classes referenced by `ModuleMain` and `MainHook`
- Read: `build.gradle.kts`, `.github/workflows/api101-build.yml`, R8/keep configuration

**Interfaces:**
- Consumes: current `main` source.
- Produces: an implementation-backed feature/architecture/compatibility inventory.

- [ ] **Step 1:** Enumerate installed process branches and active Hook composition from `ModuleMain`.
- [ ] **Step 2:** Enumerate Dock/Grid/Workstation behavior composed by `MainHook`.
- [ ] **Step 3:** Enumerate user-visible settings and defaults from `ConfigSchema` and Compose UI.
- [ ] **Step 4:** Verify build/version/R8 facts from Gradle and CI.
- [ ] **Step 5:** Cross-check claims against representative production classes and tests.

### Task 2: Rewrite public overview documentation

**Files:**
- Modify: `README.md`
- Modify: `README_EN.md`

**Interfaces:**
- Consumes: Task 1 inventory.
- Produces: accurate installation, compatibility, feature, architecture, build, and risk overview.

- [ ] **Step 1:** Replace stale version/support statements with current-main facts.
- [ ] **Step 2:** Add currently shipped feature groups omitted by the old README.
- [ ] **Step 3:** Correct process scope and Security Center wording.
- [ ] **Step 4:** Keep Chinese and English READMEs semantically aligned.

### Task 3: Rewrite technical architecture and Hook documentation

**Files:**
- Modify: `ARCHITECTURE.md`
- Modify: `HOOKS.md`

**Interfaces:**
- Consumes: Task 1 inventory.
- Produces: source-aligned runtime architecture, ownership, freshness, Hook boundaries, and R8 rules.

- [ ] **Step 1:** Document process composition and configuration authority.
- [ ] **Step 2:** Document Launcher shared PassBlur/backend/session architecture and output domains.
- [ ] **Step 3:** Document drag, shortcut popup, widget/folder/icon, Recents, HOME/keyguard, Workstation, Dock, and Security Center lifecycles.
- [ ] **Step 4:** Document fail-closed and R8/reflection boundaries.

### Task 4: Rewrite feature and contributor references

**Files:**
- Modify: `FEATURES.md`
- Modify: `CONTRIBUTING.md`
- Modify: `DIVIDER.md`
- Modify: `TODO.md`

**Interfaces:**
- Consumes: Task 1 inventory and Tasks 2–3 terminology.
- Produces: current user feature matrix, development rules, focused divider contract, and a current backlog.

- [ ] **Step 1:** Align feature descriptions with current settings and actual runtime semantics.
- [ ] **Step 2:** Remove completed/outdated TODO items and retain only current debt/future work.
- [ ] **Step 3:** Update contributor rules for typed project-owned access, cross-ClassLoader R8 hazards, source-driven PassBlur, and static-source test policy.
- [ ] **Step 4:** Keep Divider documentation scoped to the active ownership implementation.

### Task 5: Changelog/current-main note and verification

**Files:**
- Modify: `CHANGELOG.md` only if current-main changes are not represented.
- Verify: all changed Markdown files.

**Interfaces:**
- Consumes: completed documentation set.
- Produces: internally consistent documentation with no stale version labels or dead class references.

- [ ] **Step 1:** Add a concise current-main/Unreleased summary if required without rewriting release history.
- [ ] **Step 2:** Search documentation for stale `v2.2.1`, removed classes, obsolete capture paths, and unsupported process claims.
- [ ] **Step 3:** Verify every named production class/config key exists on the branch.
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest --stacktrace` and `./gradlew assembleDebug --stacktrace` through repository CI.
- [ ] **Step 5:** Review final diff for documentation-only scope and publish the PR.