#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path, old, new):
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing no-revive anchor in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


replace_once("src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java",
'''    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (host == null) return;''',
'''    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || host == null) return;''')

replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        observeFolderIconAttach(icon, glassConfig);
        if (!GlassRuntimeState.isEnabled() || !LauncherGlassHierarchy.isWorkspace(icon)) {
            try {
                View material = resolveFolderMaterial(icon);
                if (material != null) {
                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                    restoreMaterial(material);
                }
            } catch (Throwable ignored) {}
            return;
        }
        try {''',
'''    private static void attachFromFolderIcon(ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (icon == null) return;
        if (!GlassRuntimeState.isEnabled() || !LauncherGlassHierarchy.isWorkspace(icon)) {
            try {
                View material = resolveFolderMaterial(icon);
                if (material != null) {
                    LauncherGlassStaticNode old = claimedSink(material);
                    if (old != null) old.dispose();
                    CLAIMED.remove(material);
                    restoreMaterial(material);
                }
            } catch (Throwable ignored) {}
            return;
        }
        observeFolderIconAttach(icon, glassConfig);
        try {''')

replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static void scheduleFolderRecovery(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (icon == null) return;''',
'''    private static void scheduleFolderRecovery(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (!GlassRuntimeState.isEnabled() || icon == null) {
            if (icon != null) FOLDER_RECOVERY_PENDING.remove(icon);
            return;
        }''')

replace_once("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java",
'''    private static void observeFolderIconAttach(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (icon == null || FOLDER_ATTACH_LISTENERS.containsKey(icon)) return;''',
'''    private static void observeFolderIconAttach(
            ViewGroup icon, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || icon == null
                || FOLDER_ATTACH_LISTENERS.containsKey(icon)) return;''')

print("runtime no-revive gates applied")
