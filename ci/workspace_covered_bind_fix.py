from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(path, old, new, label):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


controller = ROOT / "LauncherGlassSceneController.java"
patch_once(
    controller,
    '''    static synchronized LauncherGlassSceneController findRoot(View root) {
        return root != null ? BY_ROOT.get(root) : null;
    }

''',
    '''    static synchronized LauncherGlassSceneController findRoot(View root) {
        return root != null ? BY_ROOT.get(root) : null;
    }

    static synchronized boolean isCoveredForRoot(View root) {
        LauncherGlassSceneController controller = root != null ? BY_ROOT.get(root) : null;
        return controller != null && controller.state.state() == State.COVERED;
    }

''',
    "controller covered query",
)

session = ROOT / "LauncherGlassSession.java"
patch_once(
    session,
    '''        binding = next;
        configRotation = geometry.configRotation;
''',
    '''        binding = next;
        if (LauncherGlassSceneController.isCoveredForRoot(root)) {
            // Coverage can predate the asynchronous producer bind. Do not leave a hidden
            // Workspace producer in the bridge's default continuous-on-bind state.
            Miuix307PassBlurBridge.pauseUpdates(next);
        }
        configRotation = geometry.configRotation;
''',
    "covered-after-bind pause",
)

print("covered Workspace producer bind guard applied")
