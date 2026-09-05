from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "src/main/java/com/hellovoid/liquiddock"
SESSION = MAIN / "LauncherGlassSession.java"
REGISTRY = MAIN / "LauncherGlassSessionRegistry.java"


def replace_exact(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one exact match, found {count}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


replace_exact(
    SESSION,
    '''    private void rebindProducer() {\n        if (shuttingDown) return;\n        Miuix307PassBlurBridge.Binding old = binding;\n        binding = null;\n        Miuix307PassBlurBridge.unbind(old);\n        backdropPrepared = false;\n        rollInputProducerForRebind();\n    }\n\n    private void rollInputProducerForRebind() {\n        if (shuttingDown) return;\n        postRender(() -> {\n            if (shuttingDown) return;\n            makePbufferCurrent();\n            releaseInputProducerEndpointOnRenderThread();\n            if (shuttingDown) return;\n            MainHook.log(TAG + " rolling PassBlur producer endpoint " + debugLabel());\n            createInputProducer();\n        }, null);\n    }''',
    '''    boolean suspendProducerForUnlockCapture() {\n        if (shuttingDown) return false;\n        Miuix307PassBlurBridge.Binding current = binding;\n        if (current == null) return false;\n        Miuix307PassBlurBridge.pauseUpdates(current);\n        return true;\n    }\n\n    boolean rebindProducer() {\n        return rebindProducer(null);\n    }\n\n    boolean rebindProducer(Runnable rolloverComplete) {\n        if (shuttingDown || !renderThread.isAlive()) return false;\n        Miuix307PassBlurBridge.Binding old = binding;\n        binding = null;\n        Miuix307PassBlurBridge.unbind(old);\n        backdropPrepared = false;\n        return postRender(() -> {\n            if (shuttingDown) return;\n            makePbufferCurrent();\n            releaseInputProducerEndpointOnRenderThread();\n            if (shuttingDown) return;\n            MainHook.log(TAG + " rolling PassBlur producer endpoint " + debugLabel());\n            createInputProducer();\n            if (rolloverComplete != null) mainHandler.post(rolloverComplete);\n        }, null);\n    }''')

replace_exact(
    REGISTRY,
    '''    static synchronized void suspendForUnlockCapture() {\n        int paused = 0;\n        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {\n            if (session == null || session.isShutdown()) continue;\n            try {\n                Object value = HookUtil.getField(session, "binding");\n                if (value instanceof Miuix307PassBlurBridge.Binding) {\n                    Miuix307PassBlurBridge.pauseUpdates((Miuix307PassBlurBridge.Binding) value);\n                    paused++;\n                }\n            } catch (Throwable error) {\n                MainHook.log("[DC][LauncherGlass] unlock producer pause failed: " + error);\n            }\n        }\n        MainHook.log("[DC][LauncherGlass] unlock producer capture suspended sessions=" + paused);\n    }''',
    '''    static synchronized void suspendForUnlockCapture() {\n        int paused = 0;\n        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {\n            if (session == null || session.isShutdown()) continue;\n            try {\n                if (session.suspendProducerForUnlockCapture()) paused++;\n            } catch (Throwable error) {\n                MainHook.log("[DC][LauncherGlass] unlock producer pause failed: " + error);\n            }\n        }\n        MainHook.log("[DC][LauncherGlass] unlock producer capture suspended sessions=" + paused);\n    }''')

replace_exact(
    REGISTRY,
    '''        for (LauncherGlassSession session : sessions) {\n            try {\n                java.lang.reflect.Method rebind = HookUtil.findMethodExact(\n                        session.getClass(), "rebindProducer", new Class<?>[0]);\n                rebind.invoke(session);\n                Object value = HookUtil.getField(session, "renderHandler");\n                if (!(value instanceof Handler)) throw new IllegalStateException("renderHandler unavailable");\n                Handler renderHandler = (Handler) value;\n                if (!renderHandler.post(() -> main.post(completeOne))) {\n                    throw new IllegalStateException("render queue rejected unlock sentinel");\n                }\n            } catch (Throwable error) {\n                failed.set(true);\n                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover failed: " + error);\n                main.post(completeOne);\n            }\n        }''',
    '''        for (LauncherGlassSession session : sessions) {\n            try {\n                if (!session.rebindProducer(completeOne)) {\n                    failed.set(true);\n                    MainHook.log("[DC][LauncherGlass] unlock endpoint rollover rejected by render queue");\n                    main.post(completeOne);\n                }\n            } catch (Throwable error) {\n                failed.set(true);\n                MainHook.log("[DC][LauncherGlass] unlock endpoint rollover failed: " + error);\n                main.post(completeOne);\n            }\n        }''')

replace_exact(
    REGISTRY,
    '''        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {\n            if (session == null || session.isShutdown()) continue;\n            HookUtil.invoke(session, "rebindProducer");\n            rebound++;\n        }''',
    '''        for (LauncherGlassSession session : new ArrayList<>(SESSIONS.values())) {\n            if (session == null || session.isShutdown()) continue;\n            if (session.rebindProducer()) rebound++;\n        }''')

registry = REGISTRY.read_text()
for forbidden in ("HookUtil", "renderHandler", "HookUtil.getField(session, \"binding\")"):
    if forbidden in registry:
        raise SystemExit(f"Registry still contains self-reflection marker: {forbidden}")

session = SESSION.read_text()
for required in (
    "boolean suspendProducerForUnlockCapture()",
    "boolean rebindProducer()",
    "boolean rebindProducer(Runnable rolloverComplete)",
    "return postRender(() ->",
    "mainHandler.post(rolloverComplete)",
):
    if required not in session:
        raise SystemExit(f"Session missing typed lifecycle marker: {required}")

print("LauncherGlassSession typed lifecycle patch applied")
