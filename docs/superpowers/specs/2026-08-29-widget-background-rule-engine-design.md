# Widget Background Rule Engine Design

## Problem

LiquidDock currently removes vendor widget material in two ways: a generic RemoteViews root-background path and a MAML-specific Java class that hard-codes known Weather `productId -> elementName` mappings. Live HyperOS 3 / Launcher 4.50 logs already show multiple Weather payload families with different semantic background owners (`skyColor` versus `background`), and additional widget products are expected. Continuing to add product IDs and element names in Java makes the suppression code application-specific and difficult to extend safely.

## Goals

- Expose one semantic widget-background API: `LauncherWidgetBackgroundController.claim(host)` / `release(host)` plus the deterministic loaded-MAML-root entry point.
- Keep generic vendor blur/material suppression separate from widget-specific content rules.
- Move widget-specific MAML ownership rules into a bundled XML resource.
- Support exact matching by `productId`, `appPackage`, `spanX`, `spanY`, `configSpanX`, and `configSpanY`.
- Support multiple `<hide-element>` actions in one rule.
- Preserve and restore every changed MAML element's original `mShow` value.
- Treat unknown or structurally changed widgets conservatively: log/dump, never guess a background owner.
- Keep current RemoteViews root-background ownership behavior unchanged.
- Preserve all existing widget dark-content and zero-copy glass behavior.

## Non-goals

- No user-editable rules in this implementation.
- No automatic visual/tree heuristic that guesses which MAML nodes are backgrounds.
- No recursive provider-content stripping, bitmap recolor, whole-widget inversion, or arbitrary reflection actions configured by XML.
- No network-fetched rule catalog.

## Architecture

### `LauncherWidgetBackgroundController`

This becomes the only semantic widget-background entry point used by Launcher hooks.

- `claim(View host)` applies generic vendor material suppression and, for MAML hosts, evaluates built-in background rules.
- `claimLoadedMamlRoot(View host, Object root)` handles the deterministic `MamlView.initMamlview(...)` post-init boundary without repeating vendor ownership work.
- `release(View host)` restores generic RemoteViews background ownership and every MAML claim.

Folder material remains a separate vendor-material operation because folders are not widgets.

### `LauncherGlassVendorMaterialSuppressor`

This remains a low-level helper only. It owns:

- clearing Launcher blur/material APIs;
- RemoteViews direct-child `android.R.id.widget_frame` background save/clear/restore;
- MAML `enable_background_blur` variable wiring;
- folder material suppression.

It must not contain product IDs, app packages, MAML element names, or invoke widget-specific rule logic.

### `WidgetBackgroundIdentity`

A pure value object containing:

- `type` (`maml` for the current rule executor);
- `productId`;
- `appPackage`;
- `spanX`, `spanY`;
- `configSpanX`, `configSpanY`.

Unknown values are represented as null / `-1` and only match rules that do not constrain those fields.

### `WidgetBackgroundRuleEngine`

A pure Java rule loader and matcher. Rules are loaded once from the module classpath resource `widget_background_rules.xml` using the module ClassLoader, never Launcher resources.

Matching semantics:

1. Every attribute specified by a rule must equal the corresponding identity field.
2. The highest-specificity matching rule wins.
3. `productId` is weighted above package/span fallback so exact product rules always beat broad diagnostics/fallback rules.
4. XML document order is the stable tie-breaker.
5. A matching rule may have zero hide actions; that is a diagnostic-only rule.

If XML parsing/resource loading fails, the engine returns no destructive rules and logs the failure once.

### `LauncherMamlBackgroundRuleExecutor`

This Android/Xposed-facing executor:

1. Reads `MaMlWidgetInfo` identity from the host.
2. Asks the rule engine for the best rule.
3. Resolves all configured `<hide-element name="..."/>` targets with `ScreenElementRoot.findElement()` before mutating anything.
4. If any configured target is absent, performs no partial mutation and dumps `mElements` once for that root.
5. If all targets resolve, stores each element and its original `mShow`, then calls `show(false)` on each.
6. `release(host)` restores every stored original `mShow` value.

This class contains no Weather constants or product-specific branches.

## Rule XML

Bundled resource: `src/main/resources/widget_background_rules.xml`.

```xml
<?xml version="1.0" encoding="utf-8"?>
<widget-background-rules version="1">
    <rule id="miui-weather-compact"
          type="maml"
          productId="b8006e83-c497-4642-9815-f674b82842b0">
        <hide-element name="skyColor" />
    </rule>

    <rule id="miui-weather-large"
          type="maml"
          productId="c989887f-fa0d-4963-8c57-896c03e37efc">
        <hide-element name="background" />
    </rule>

    <rule id="miui-weather-wide"
          type="maml"
          productId="bc0f0cd2-43fd-4323-8061-55a8bc997e1f">
        <hide-element name="background" />
    </rule>

    <rule id="miui-weather-diagnostic"
          type="maml"
          appPackage="com.miui.weather2" />
</widget-background-rules>
```

The final package-only Weather rule intentionally has no actions. It recognizes future Weather products for diagnostics while preventing a package-wide guess such as always hiding `background`.

## Failure and restore behavior

- Unknown rule: no MAML mutation.
- Diagnostic-only rule: identity is logged and `mElements` is dumped once when useful.
- Missing configured element: no partial hide; dump once.
- Host/root changes: restore the prior claim before applying a new one.
- Rule engine load failure: generic vendor material suppression remains active, but MAML content nodes are untouched.
- Disable/release: restore original RemoteViews Drawable and every MAML element's original `mShow`.

## Tests

- XML parser accepts all supported match attributes and multiple hide actions.
- Exact `productId` rule outranks package fallback.
- Unknown identity produces no destructive rule.
- Built-in XML contains the three currently observed Weather products and a diagnostic-only package fallback.
- Production Java contains none of those Weather product IDs or `com.miui.weather2`.
- Multiple MAML claims restore their original `mShow` values.
- Missing one target prevents partial mutation.
- Existing RemoteViews direct-root background contracts remain green.
- APK build contains `widget_background_rules.xml` as a Java/classpath resource.

## Future customization

User-editable rules are explicitly deferred. `TODO.md` will track a future UI/import layer that validates user rules against the same schema, supports per-rule enable/disable and import/export, and preserves the conservative no-guess/no-arbitrary-reflection safety model.
