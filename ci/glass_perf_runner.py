from pathlib import Path
import subprocess

path = Path(__file__).with_name("glass_perf_apply.py")
text = path.read_text()
old = '''replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java",
''' + "'''" + '''    void requestLifecycleRefresh() {
        if (disposed) return;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestLifecycleRefresh();
    }
''' + "'''" + ''',
''' + "'''" + '''    void requestLifecycleRefresh() {
        if (disposed) return;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestStaticRedraw();
    }
''' + "'''" + ''')
'''
new = '''replace_once("src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java",
''' + "'''" + '''    void requestLifecycleRefresh() {
        if (disposed) return;
        geometryDirty = true;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestLifecycleRefresh();
    }
''' + "'''" + ''',
''' + "'''" + '''    void requestLifecycleRefresh() {
        if (disposed) return;
        geometryDirty = true;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestStaticRedraw();
    }
''' + "'''" + ''')
'''
if text.count(old) != 1:
    raise RuntimeError(f"static-node adaptation count={text.count(old)}")
path.write_text(text.replace(old, new, 1))
subprocess.run(["python3", str(path)], check=True)
