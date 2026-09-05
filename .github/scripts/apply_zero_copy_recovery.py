from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java")
text = path.read_text()

replacements = [
    (
        "    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);\n"
        "    private final float[] textureMatrix = new float[16];",
        "    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);\n"
        "    private final ZeroCopyProducerRecoveryState producerRecovery =\n"
        "            new ZeroCopyProducerRecoveryState();\n"
        "    private final float[] textureMatrix = new float[16];",
        1,
    ),
    (
        "    private volatile boolean activationExhausted;\n"
        "    private volatile boolean hasConsumedFrame;\n"
        "    private volatile boolean producerRebindPending;\n",
        "",
        1,
    ),
    (
        "    boolean isActivationExhausted() {\n"
        "        return activationExhausted;\n"
        "    }",
        "    boolean isActivationExhausted() {\n"
        "        return producerRecovery.isActivationExhausted();\n"
        "    }",
        1,
    ),
    (
        "        if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));",
        "        if (producerRecovery.hasFreshFrame()) renderHandler.post(() -> drawLatestFrame(false));",
        4,
    ),
    (
        "    void rebindProducer(String reason) {\n"
        "        if (shuttingDown) return;\n"
        "        if (producerRebindPending) return;\n"
        "        producerRebindPending = true;\n"
        "        Miuix307PassBlurBridge.Binding stale = binding;\n"
        "        binding = null;\n"
        "        Miuix307PassBlurBridge.unbind(stale);\n"
        "        gpuBackdropActive = false;\n"
        "        activationExhausted = false;\n"
        "        hasConsumedFrame = false;\n"
        "        frameAvailable.set(false);",
        "    void rebindProducer(String reason) {\n"
        "        if (shuttingDown) return;\n"
        "        ZeroCopyProducerRecoveryState.Decision recovery =\n"
        "                producerRecovery.onRebindRequested();\n"
        "        if (!recovery.accepted) return;\n"
        "        if (recovery.clearFrameworkBinding) {\n"
        "            Miuix307PassBlurBridge.Binding stale = binding;\n"
        "            binding = null;\n"
        "            Miuix307PassBlurBridge.unbind(stale);\n"
        "        }\n"
        "        gpuBackdropActive = false;\n"
        "        if (recovery.clearFrameAvailable) frameAvailable.set(false);",
        1,
    ),
    (
        "        renderHandler.post(() -> recreateInputProducer(reason));\n"
        "    }",
        "        if (recovery.recreateProducer) {\n"
        "            renderHandler.post(() -> recreateInputProducer(reason));\n"
        "        }\n"
        "    }",
        1,
    ),
    (
        "            MainHook.log(TAG + \" input producer recreated reason=\" + reason);\n"
        "            post(() -> bindProducerWhenReady(0));\n"
        "        } catch (Throwable error) {\n"
        "            producerRebindPending = false;\n"
        "            fail(\"producer recreate\", error);",
        "            MainHook.log(TAG + \" input producer recreated reason=\" + reason);\n"
        "            ZeroCopyProducerRecoveryState.Decision recovery =\n"
        "                    producerRecovery.onProducerRecreated();\n"
        "            if (recovery.requestBind) post(() -> bindProducerWhenReady(0));\n"
        "        } catch (Throwable error) {\n"
        "            producerRecovery.onRecreateFailed();\n"
        "            fail(\"producer recreate\", error);",
        1,
    ),
    (
        "        shuttingDown = true;\n"
        "        producerRebindPending = false;\n"
        "        gpuBackdropActive = false;",
        "        shuttingDown = true;\n"
        "        producerRecovery.onShutdown();\n"
        "        gpuBackdropActive = false;",
        1,
    ),
    (
        "                input.getTransformMatrix(textureMatrix);\n"
        "                hasConsumedFrame = true;\n"
        "            }\n"
        "            if (!hasConsumedFrame) return;",
        "                input.getTransformMatrix(textureMatrix);\n"
        "                producerRecovery.onFreshFrameConsumed();\n"
        "            }\n"
        "            if (!producerRecovery.hasFreshFrame()) return;",
        1,
    ),
    (
        "        producerRebindPending = false;\n"
        "        configRotation = current.configRotation;",
        "        producerRecovery.onBindSucceeded();\n"
        "        configRotation = current.configRotation;",
        1,
    ),
    (
        "        boundConfigRotation = current.configRotation;\n"
        "        activationExhausted = false;\n"
        "        stageBDiagnosticsLogged = false;",
        "        boundConfigRotation = current.configRotation;\n"
        "        stageBDiagnosticsLogged = false;",
        1,
    ),
    (
        "        if (attempt >= MAX_BIND_RETRY_FRAMES) {\n"
        "            activationExhausted = true;\n"
        "            producerRebindPending = false;",
        "        if (attempt >= MAX_BIND_RETRY_FRAMES) {\n"
        "            producerRecovery.onBindExhausted();",
        1,
    ),
    (
        "        boundConfigRotation = geometry.configRotation;\n"
        "        hasConsumedFrame = false;\n"
        "        frameAvailable.set(false);",
        "        boundConfigRotation = geometry.configRotation;\n"
        "        ZeroCopyProducerRecoveryState.Decision invalidated =\n"
        "                producerRecovery.onGeometryInvalidated();\n"
        "        if (invalidated.clearFrameAvailable) frameAvailable.set(false);",
        1,
    ),
    (
        "    private void fail(String stage, Throwable error) {\n"
        "        activationExhausted = true;\n"
        "        gpuBackdropActive = false;",
        "    private void fail(String stage, Throwable error) {\n"
        "        producerRecovery.onTerminalFailure();\n"
        "        gpuBackdropActive = false;",
        1,
    ),
]

for old, new, expected in replacements:
    count = text.count(old)
    if count != expected:
        raise SystemExit(
            f"expected {expected} match(es), got {count}: {old[:120]!r}")
    text = text.replace(old, new)

if "hasConsumedFrame" in text or "producerRebindPending" in text or "activationExhausted" in text:
    raise SystemExit("raw producer recovery field references remain")

path.write_text(text)
