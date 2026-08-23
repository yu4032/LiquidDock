from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected one match, found {count}')
    path.write_text(text.replace(old, new, 1))

patch_once(
    'Miuix307PassBlurBridge.java',
    '''        final float scale;\n        final String rootName;\n        boolean bound = true;\n''',
    '''        final float scale;\n        final String rootName;\n        // Immutable snapshots. ViewRootImpl may mutate the same SurfaceControl Java wrapper\n        // to point at a new BLAST/native layer, so keeping only rootSurface aliases away the\n        // old generation identity that a later recovery needs to compare.\n        final int viewRootIdentity;\n        final int surfaceSequenceId;\n        final int rootLayerId;\n        boolean bound = true;\n''',
    'Binding stores immutable native generation snapshots',
)

patch_once(
    'Miuix307PassBlurBridge.java',
    '''                Method setMiBlurWinExc,\n                float scale,\n                String rootName) {\n            this.rootSurface = rootSurface;\n            this.setPassBlurSurface = setPassBlurSurface;\n            this.setUpdateTextureFlag = setUpdateTextureFlag;\n            this.setMiBlurWinExc = setMiBlurWinExc;\n            this.scale = scale;\n            this.rootName = rootName;\n        }\n''',
    '''                Method setMiBlurWinExc,\n                float scale,\n                String rootName,\n                int viewRootIdentity,\n                int surfaceSequenceId,\n                int rootLayerId) {\n            this.rootSurface = rootSurface;\n            this.setPassBlurSurface = setPassBlurSurface;\n            this.setUpdateTextureFlag = setUpdateTextureFlag;\n            this.setMiBlurWinExc = setMiBlurWinExc;\n            this.scale = scale;\n            this.rootName = rootName;\n            this.viewRootIdentity = viewRootIdentity;\n            this.surfaceSequenceId = surfaceSequenceId;\n            this.rootLayerId = rootLayerId;\n        }\n''',
    'Binding constructor receives native generation snapshots',
)

patch_once(
    'Miuix307PassBlurBridge.java',
    '''            String rootName = surfaceName(rootSurface);\n            String[] exclusions = new String[]{\n''',
    '''            String rootName = surfaceName(rootSurface);\n            int viewRootIdentity = System.identityHashCode(viewRoot);\n            int surfaceSequenceId = readSurfaceSequenceId(viewRoot);\n            int rootLayerId = surfaceLayerId(rootSurface);\n            String[] exclusions = new String[]{\n''',
    'bind snapshots ViewRoot and native layer generation',
)

patch_once(
    'Miuix307PassBlurBridge.java',
    '''                    setMiBlurWinExc,\n                    scale,\n                    rootName);\n\n            MainHook.log(TAG + " PassBlur producer bound scale=" + scale\n''',
    '''                    setMiBlurWinExc,\n                    scale,\n                    rootName,\n                    viewRootIdentity,\n                    surfaceSequenceId,\n                    rootLayerId);\n\n            MainHook.log(TAG + " PassBlur producer bound scale=" + scale\n''',
    'Binding construction stores native generation snapshots',
)

patch_once(
    'Miuix307PassBlurBridge.java',
    '''                    + " root=" + rootName\n                    + " output=TextureView-in-root"\n''',
    '''                    + " root=" + rootName\n                    + " layerId=" + rootLayerId\n                    + " surfaceSeq=" + surfaceSequenceId\n                    + " viewRootId=" + viewRootIdentity\n                    + " output=TextureView-in-root"\n''',
    'bind log exposes immutable generation snapshots',
)

patch_once(
    'Miuix307PassBlurBridge.java',
    '''    private static String surfaceName(SurfaceControl surface) {\n''',
    '''    static int surfaceLayerId(SurfaceControl surface) {\n        if (surface == null) return -1;\n        try {\n            Method method = SurfaceControl.class.getDeclaredMethod("getLayerId");\n            method.setAccessible(true);\n            Object value = method.invoke(surface);\n            return value instanceof Number ? ((Number) value).intValue() : -1;\n        } catch (Throwable ignored) {\n            return -1;\n        }\n    }\n\n    static int readSurfaceSequenceId(Object viewRoot) {\n        if (viewRoot == null) return -1;\n        Class<?> type = viewRoot.getClass();\n        while (type != null) {\n            try {\n                Method method = type.getDeclaredMethod("getSurfaceSequenceId");\n                method.setAccessible(true);\n                Object value = method.invoke(viewRoot);\n                if (value instanceof Number) return ((Number) value).intValue();\n            } catch (NoSuchMethodException ignored) {\n                type = type.getSuperclass();\n                continue;\n            } catch (Throwable ignored) {\n                break;\n            }\n        }\n        type = viewRoot.getClass();\n        while (type != null) {\n            try {\n                java.lang.reflect.Field field = type.getDeclaredField("mSurfaceSequenceId");\n                field.setAccessible(true);\n                Object value = field.get(viewRoot);\n                return value instanceof Number ? ((Number) value).intValue() : -1;\n            } catch (NoSuchFieldException ignored) {\n                type = type.getSuperclass();\n            } catch (Throwable ignored) {\n                return -1;\n            }\n        }\n        return -1;\n    }\n\n    private static String surfaceName(SurfaceControl surface) {\n''',
    'reflection helpers read immutable native generation ids',
)

patch_once(
    'LauncherGlassSession.java',
    '''        final SurfaceControl rootSurface;\n        final int insetLeft;\n''',
    '''        final SurfaceControl rootSurface;\n        final int viewRootIdentity;\n        final int surfaceSequenceId;\n        final int rootLayerId;\n        final int insetLeft;\n''',
    'ProducerGeometry stores generation snapshots',
)

patch_once(
    'LauncherGlassSession.java',
    '''                int surfaceWidth, int surfaceHeight, int bufferWidth, int bufferHeight,\n                int configRotation, SurfaceControl rootSurface,\n                int insetLeft, int insetTop, int insetRight, int insetBottom) {\n''',
    '''                int surfaceWidth, int surfaceHeight, int bufferWidth, int bufferHeight,\n                int configRotation, SurfaceControl rootSurface,\n                int viewRootIdentity, int surfaceSequenceId, int rootLayerId,\n                int insetLeft, int insetTop, int insetRight, int insetBottom) {\n''',
    'ProducerGeometry constructor accepts generation snapshots',
)

patch_once(
    'LauncherGlassSession.java',
    '''            this.configRotation = configRotation;\n            this.rootSurface = rootSurface;\n            this.insetLeft = insetLeft;\n''',
    '''            this.configRotation = configRotation;\n            this.rootSurface = rootSurface;\n            this.viewRootIdentity = viewRootIdentity;\n            this.surfaceSequenceId = surfaceSequenceId;\n            this.rootLayerId = rootLayerId;\n            this.insetLeft = insetLeft;\n''',
    'ProducerGeometry constructor stores generation snapshots',
)

patch_once(
    'LauncherGlassSession.java',
    '''            SurfaceControl surfaceControl = value instanceof SurfaceControl\n                    ? (SurfaceControl) value : null;\n            return new ProducerGeometry(surfaceWidth, surfaceHeight,\n                    bufferWidth, bufferHeight, rotation, surfaceControl,\n                    surfaceInsets.left, surfaceInsets.top,\n''',
    '''            SurfaceControl surfaceControl = value instanceof SurfaceControl\n                    ? (SurfaceControl) value : null;\n            int viewRootIdentity = System.identityHashCode(viewRoot);\n            int surfaceSequenceId = Miuix307PassBlurBridge.readSurfaceSequenceId(viewRoot);\n            int rootLayerId = Miuix307PassBlurBridge.surfaceLayerId(surfaceControl);\n            return new ProducerGeometry(surfaceWidth, surfaceHeight,\n                    bufferWidth, bufferHeight, rotation, surfaceControl,\n                    viewRootIdentity, surfaceSequenceId, rootLayerId,\n                    surfaceInsets.left, surfaceInsets.top,\n''',
    'readSurfaceGeometry snapshots current ViewRoot/native generation',
)

old = '''        Miuix307PassBlurBridge.Binding current = binding;\n        if (current != null && (!current.rootSurface.isValid()\n                || !isSameSurface(current.rootSurface, geometry.rootSurface))) {\n            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);\n'''
new = '''        Miuix307PassBlurBridge.Binding current = binding;\n        if (current != null && (!current.rootSurface.isValid()\n                || !sameProducerSurfaceGeneration(current, geometry))) {\n            long nextGeneration = LauncherGlassSceneController.invalidateForProducerChange(root);\n'''
patch_once('LauncherGlassSession.java', old, new,
           'fresh recovery compares immutable producer generation')

patch_once(
    'LauncherGlassSession.java',
    '''        if (!current.bound || !current.rootSurface.isValid()\n                || !isSameSurface(current.rootSurface, geometry.rootSurface)) {\n''',
    '''        if (!current.bound || !current.rootSurface.isValid()\n                || !sameProducerSurfaceGeneration(current, geometry)) {\n''',
    'fresh recovery final guard compares immutable generation',
)

patch_once(
    'LauncherGlassSession.java',
    '''        boolean surfaceChanged = current != null && (!current.rootSurface.isValid()\n                || !isSameSurface(current.rootSurface, geometry.rootSurface));\n''',
    '''        boolean surfaceChanged = current != null && (!current.rootSurface.isValid()\n                || !sameProducerSurfaceGeneration(current, geometry));\n''',
    'producer geometry detects same-wrapper native generation change',
)

patch_once(
    'LauncherGlassSession.java',
    '''                MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName\n                        + " new=" + geometry.rootSurface);\n''',
    '''                MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName\n                        + " oldLayerId=" + current.rootLayerId\n                        + " new=" + geometry.rootSurface\n                        + " newLayerId=" + geometry.rootLayerId\n                        + " oldSurfaceSeq=" + current.surfaceSequenceId\n                        + " newSurfaceSeq=" + geometry.surfaceSequenceId);\n''',
    'geometry generation log exposes old/new layer ids',
)

# The recovery path has its own generation-change log before refreshProducerGeometryOnUi.
patch_once(
    'LauncherGlassSession.java',
    '''            MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName\n                    + " new=" + geometry.rootSurface);\n''',
    '''            MainHook.log(TAG + " producer Surface generation changed old=" + current.rootName\n                    + " oldLayerId=" + current.rootLayerId\n                    + " new=" + geometry.rootSurface\n                    + " newLayerId=" + geometry.rootLayerId\n                    + " oldSurfaceSeq=" + current.surfaceSequenceId\n                    + " newSurfaceSeq=" + geometry.surfaceSequenceId);\n''',
    'fresh recovery generation log exposes old/new layer ids',
)

patch_once(
    'LauncherGlassSession.java',
    '''    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {\n''',
    '''    private static boolean sameProducerSurfaceGeneration(\n            Miuix307PassBlurBridge.Binding current, ProducerGeometry geometry) {\n        if (current == null || geometry == null) return false;\n        // ViewRoot replacement is a generation change even if WMS happens to reuse a layer id.\n        if (current.viewRootIdentity != 0 && geometry.viewRootIdentity != 0\n                && current.viewRootIdentity != geometry.viewRootIdentity) {\n            return false;\n        }\n        boolean comparedImmutableGeneration = false;\n        if (current.rootLayerId >= 0 && geometry.rootLayerId >= 0) {\n            comparedImmutableGeneration = true;\n            if (current.rootLayerId != geometry.rootLayerId) return false;\n        }\n        if (current.surfaceSequenceId >= 0 && geometry.surfaceSequenceId >= 0) {\n            comparedImmutableGeneration = true;\n            if (current.surfaceSequenceId != geometry.surfaceSequenceId) return false;\n        }\n        if (comparedImmutableGeneration) return true;\n        // Last-resort compatibility fallback only. It is intentionally not the primary key because\n        // ViewRootImpl can mutate the same SurfaceControl wrapper to a new native BLAST layer.\n        return isSameSurface(current.rootSurface, geometry.rootSurface);\n    }\n\n    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {\n''',
    'immutable producer generation comparator',
)

print('Workspace immutable native Surface generation patch applied')
