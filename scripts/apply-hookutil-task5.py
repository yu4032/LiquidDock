from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/hellovoid/liquiddock"
TARGETS = (
    "LauncherWidgetDarkContentAdapter.java",
    "LauncherMamlBackgroundRuleExecutor.java",
    "LauncherWidgetComponentSelectionExecutor.java",
    "LauncherWidgetComponentDiscovery.java",
    "LauncherWidgetTransitionCoordinator.java",
    "LauncherWidgetTransitionHook.java",
    "SystemUiKeyguardGoneRuntime.java",
    "SystemUiKeyguardGoneSource.java",
    "SystemUiHomeTransitionRuntime.java",
    "SystemUiHomeTransitionSource.java",
)


def replace_exact(path: Path, old: str, new: str, count: int = 1) -> None:
    text = path.read_text()
    actual = text.count(old)
    if actual != count:
        raise SystemExit(f"{path}: expected {count} exact matches, found {actual}: {old[:100]!r}")
    path.write_text(text.replace(old, new, count))


def migrate_with_helper(name: str, expected_calls: int, anchor: str, tag_expression: str) -> None:
    path = MAIN / name
    text = path.read_text()
    actual = text.count("HookUtil.invoke(")
    if actual != expected_calls:
        raise SystemExit(f"{name}: expected {expected_calls} old calls, found {actual}")
    text = text.replace("HookUtil.invoke(", "invokeOptional(")
    if text.count(anchor) != 1:
        raise SystemExit(f"{name}: helper anchor mismatch: {anchor!r}")
    helper = f'''    private static Object invokeOptional(Object target, String methodName, Object... args) {{\n        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(target, methodName, args);\n        if (!result.succeeded()) {{\n            MainHook.log({tag_expression} + methodName + " unavailable: " + result.failure());\n            return null;\n        }}\n        return result.value();\n    }}\n\n'''
    path.write_text(text.replace(anchor, helper + anchor, 1))


migrate_with_helper(
    "LauncherWidgetDarkContentAdapter.java", 5,
    "    private static void applyTextTree(View view) {",
    '"[DC][WidgetDarkContent] "')

migrate_with_helper(
    "LauncherMamlBackgroundRuleExecutor.java", 5,
    "    private static boolean allHidden(List<Object> elements) {",
    'LOG_TAG + " "')

migrate_with_helper(
    "LauncherWidgetComponentSelectionExecutor.java", 4,
    "    private static List<WidgetComponentStore.Descriptor> selectors(",
    '"[DC][WidgetComponent] "')

# Discovery: read-only probes; distinguish unavailable from successful null.
replace_exact(
    MAIN / "LauncherWidgetComponentDiscovery.java",
    '            Object namedTarget = name.isEmpty() ? null : HookUtil.invoke(root, "findElement", name);',
    '''            HookUtil.InvocationResult<Object> namedTargetResult = name.isEmpty()\n                    ? null : HookUtil.tryInvoke(root, "findElement", name);\n            Object namedTarget = namedTargetResult != null && namedTargetResult.succeeded()\n                    ? namedTargetResult.value() : null;''')
replace_exact(
    MAIN / "LauncherWidgetComponentDiscovery.java",
    '''    private static Number invokeNumber(Object target, String methodName) {\n        if (target == null) return null;\n        try {\n            Object value = HookUtil.invoke(target, methodName);\n            return value instanceof Number ? (Number) value : null;\n        } catch (Throwable ignored) {\n            return null;\n        }\n    }''',
    '''    private static Number invokeNumber(Object target, String methodName) {\n        if (target == null) return null;\n        HookUtil.InvocationResult<Object> valueResult = HookUtil.tryInvoke(target, methodName);\n        if (!valueResult.succeeded()) return null;\n        Object value = valueResult.value();\n        return value instanceof Number ? (Number) value : null;\n    }''')

# Widget transition probes are optional: stale glass stays suppressed / transition skips safely.
replace_exact(
    MAIN / "LauncherWidgetTransitionCoordinator.java",
    '        HookUtil.invoke(node, "hideImmediately");',
    '''        HookUtil.InvocationResult<Object> immediateResult =\n                HookUtil.tryInvoke(node, "hideImmediately");\n        if (!immediateResult.succeeded()) {\n            MainHook.log(TAG + " immediate hide unavailable: " + immediateResult.failure());\n        }''')
replace_exact(
    MAIN / "LauncherWidgetTransitionCoordinator.java",
    '''            Object generationValue = HookUtil.invoke(stateMachine, "generation");\n            Object stateValue = HookUtil.invoke(stateMachine, "state");''',
    '''            HookUtil.InvocationResult<Object> generationResult =\n                    HookUtil.tryInvoke(stateMachine, "generation");\n            HookUtil.InvocationResult<Object> stateResult =\n                    HookUtil.tryInvoke(stateMachine, "state");\n            if (!generationResult.succeeded() || !stateResult.succeeded()) return null;\n            Object generationValue = generationResult.value();\n            Object stateValue = stateResult.value();''')
replace_exact(
    MAIN / "LauncherWidgetTransitionHook.java",
    '''    private static View resolveAnimTargetContainer(Object target) {\n        Object value = HookUtil.invoke(target, "getAnimTargetContainerView");\n        return value instanceof View ? (View) value : null;\n    }''',
    '''    private static View resolveAnimTargetContainer(Object target) {\n        HookUtil.InvocationResult<Object> containerResult =\n                HookUtil.tryInvoke(target, "getAnimTargetContainerView");\n        Object value = containerResult.succeeded() ? containerResult.value() : null;\n        return value instanceof View ? (View) value : null;\n    }''')

# SystemUI application lookup is optional; unavailable means no registration/publication.
replace_exact(
    MAIN / "SystemUiKeyguardGoneRuntime.java",
    '''        Object application = HookUtil.invokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        if (application instanceof Context) {''',
    '''        HookUtil.InvocationResult<Object> applicationResult = HookUtil.tryInvokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        Object application = applicationResult.succeeded() ? applicationResult.value() : null;\n        if (application instanceof Context) {''')
replace_exact(
    MAIN / "SystemUiHomeTransitionRuntime.java",
    '''        Object application = HookUtil.invokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        if (application instanceof Context) ensureRegistered((Context) application);''',
    '''        HookUtil.InvocationResult<Object> applicationResult = HookUtil.tryInvokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        Object application = applicationResult.succeeded() ? applicationResult.value() : null;\n        if (application instanceof Context) ensureRegistered((Context) application);''')

replace_exact(
    MAIN / "SystemUiKeyguardGoneSource.java",
    '''    private static Object read(Object owner, String getter, String field) {\n        Object value = HookUtil.invoke(owner, getter);\n        if (value != null) return value;''',
    '''    private static Object read(Object owner, String getter, String field) {\n        HookUtil.InvocationResult<Object> getterResult = HookUtil.tryInvoke(owner, getter);\n        Object value = getterResult.succeeded() ? getterResult.value() : null;\n        if (value != null) return value;''')
replace_exact(
    MAIN / "SystemUiKeyguardGoneSource.java",
    '''        Object application = HookUtil.invokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        if (!(application instanceof Context)) {''',
    '''        HookUtil.InvocationResult<Object> applicationResult = HookUtil.tryInvokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        Object application = applicationResult.succeeded() ? applicationResult.value() : null;\n        if (!(application instanceof Context)) {''')
replace_exact(
    MAIN / "SystemUiHomeTransitionSource.java",
    '''        Object application = HookUtil.invokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        if (!(application instanceof Context)) {''',
    '''        HookUtil.InvocationResult<Object> applicationResult = HookUtil.tryInvokeStatic(\n                "android.app.ActivityThread", "currentApplication");\n        Object application = applicationResult.succeeded() ? applicationResult.value() : null;\n        if (!(application instanceof Context)) {''')

for name in TARGETS:
    source = (MAIN / name).read_text()
    if "HookUtil.invoke(" in source or "HookUtil.invokeStatic(" in source:
        raise SystemExit(f"{name}: legacy silent invocation remains")

print("Task 5 exact patch applied")
