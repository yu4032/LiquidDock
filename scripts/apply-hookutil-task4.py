from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/hellovoid/liquiddock"
TEST = ROOT / "src/test/java/com/hellovoid/liquiddock"


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:80]!r}")
    path.write_text(text.replace(old, new))


replace_exact(
    MAIN / "Miuix307MaterialPipeline.java",
    '''                            int itemCount = (Integer) HookUtil.invoke(\n                                    chain.getThisObject(), "getItemCount");''',
    '''                            int itemCount = (Integer) HookUtil.requireInvoke(\n                                    chain.getThisObject(), "getItemCount");''')
replace_exact(
    MAIN / "Miuix307MaterialPipeline.java",
    '''        try {\n            Object value = HookUtil.invoke(hotSeats, "getHotSeatsBackground");\n            if (value instanceof View && isSupportedBackground((View) value)) {\n                MainHook.log("[DC] getHotSeatsBackground returned " + value.getClass().getName());\n                return (View) value;\n            }\n        } catch (Throwable ignored) {}''',
    '''        HookUtil.InvocationResult<Object> backgroundResult =\n                HookUtil.tryInvoke(hotSeats, "getHotSeatsBackground");\n        Object value = backgroundResult.succeeded() ? backgroundResult.value() : null;\n        if (value instanceof View && isSupportedBackground((View) value)) {\n            MainHook.log("[DC] getHotSeatsBackground returned " + value.getClass().getName());\n            return (View) value;\n        }''')

replace_exact(
    MAIN / "DockStrokeRenderer.java",
    '''        try {\n            HookUtil.invokeStatic("com.miui.home.launcher.common.MiShadowUtils",\n                    "applyViewShadow", host, color, 0f, 0f, radius, 1f);\n        } catch (Throwable error) {\n            MainHook.log("[DC] native stroke outer shadow unavailable: " + error);\n        }''',
    '''        HookUtil.InvocationResult<Object> shadowResult = HookUtil.tryInvokeStatic(\n                "com.miui.home.launcher.common.MiShadowUtils",\n                "applyViewShadow", host, color, 0f, 0f, radius, 1f);\n        if (!shadowResult.succeeded()) {\n            MainHook.log("[DC] native stroke outer shadow unavailable: "\n                    + shadowResult.failure());\n        }''')

replace_exact(
    MAIN / "MiuixFolderGlassHook.java",
    '''        Object value = HookUtil.invoke(icon, "getCover");\n        if (!(value instanceof View)) return;''',
    '''        HookUtil.InvocationResult<Object> coverResult = HookUtil.tryInvoke(icon, "getCover");\n        Object value = coverResult.succeeded() ? coverResult.value() : null;\n        if (!(value instanceof View)) return;''')
replace_exact(
    MAIN / "MiuixFolderGlassHook.java",
    '''        Object paintValue = HookUtil.invoke(drawable, "getPaint");\n        if (!(paintValue instanceof Paint)) return;''',
    '''        HookUtil.InvocationResult<Object> paintResult = HookUtil.tryInvoke(drawable, "getPaint");\n        Object paintValue = paintResult.succeeded() ? paintResult.value() : null;\n        if (!(paintValue instanceof Paint)) return;''')
replace_exact(
    MAIN / "MiuixFolderGlassHook.java",
    '''            Object paintValue = HookUtil.invoke(drawable, "getPaint");\n            if (!(paintValue instanceof Paint)) continue;''',
    '''            HookUtil.InvocationResult<Object> paintResult = HookUtil.tryInvoke(drawable, "getPaint");\n            Object paintValue = paintResult.succeeded() ? paintResult.value() : null;\n            if (!(paintValue instanceof Paint)) continue;''')

replace_exact(
    MAIN / "MiuixLauncherStaticGlassHook.java",
    '''        Object current = HookUtil.invoke(workspace, "getCurrentCellLayout");''',
    '''        HookUtil.InvocationResult<Object> currentResult =\n                HookUtil.tryInvoke(workspace, "getCurrentCellLayout");\n        Object current = currentResult.succeeded() ? currentResult.value() : null;''')
replace_exact(
    MAIN / "MiuixLauncherStaticGlassHook.java",
    '''                            Object target = HookUtil.invoke(owner, "getAnimTarget");''',
    '''                            HookUtil.InvocationResult<Object> targetResult =\n                                    HookUtil.tryInvoke(owner, "getAnimTarget");\n                            Object target = targetResult.succeeded() ? targetResult.value() : null;''')
replace_exact(
    MAIN / "MiuixLauncherStaticGlassHook.java",
    '''                                        Object draw = HookUtil.invoke(owner, "isDrawIcon");\n                                        drawIcon = draw instanceof Boolean && ((Boolean) draw);''',
    '''                                        HookUtil.InvocationResult<Object> drawResult =\n                                                HookUtil.tryInvoke(owner, "isDrawIcon");\n                                        Object draw = drawResult.succeeded() ? drawResult.value() : null;\n                                        drawIcon = draw instanceof Boolean && ((Boolean) draw);''')
replace_exact(
    MAIN / "MiuixLauncherStaticGlassHook.java",
    '''        Object value = HookUtil.invoke(launcher, "getWorkspace");''',
    '''        HookUtil.InvocationResult<Object> workspaceResult =\n                HookUtil.tryInvoke(launcher, "getWorkspace");\n        Object value = workspaceResult.succeeded() ? workspaceResult.value() : null;''')

replace_exact(
    TEST / "LauncherGlassVendorMaterialSuppressionContractTest.java",
    '''        assertTrue(folder.contains("HookUtil.invoke(drawable, \\"getPaint\\")"));''',
    '''        assertTrue(folder.contains("HookUtil.tryInvoke(drawable, \\"getPaint\\")"));\n        assertTrue(folder.contains("paintResult.succeeded()"));''')

for name in (
    "Miuix307MaterialPipeline.java",
    "DockStrokeRenderer.java",
    "MiuixFolderGlassHook.java",
    "MiuixLauncherStaticGlassHook.java",
):
    source = (MAIN / name).read_text()
    if "HookUtil.invoke(" in source or "HookUtil.invokeStatic(" in source:
        raise SystemExit(f"old silent invocation remains in {name}")

print("Task 4 exact patch applied")
