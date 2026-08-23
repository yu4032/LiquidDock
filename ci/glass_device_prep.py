#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"
s = p.read_text()
marker12 = "            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);\n"
reader12 = "            ConfigReader configReader = ConfigReader.load();\n"
call12 = "            new MainHook().install(classLoader);\n"
dock12 = "            DockBottomGeometryHook.install(classLoader);\n"
if marker12 not in s or reader12 not in s or call12 not in s or dock12 not in s:
    raise SystemExit("ModuleMain experiment anchor missing")
# Normalize only the historical patch anchors. Java semantics are unchanged.
s = s.replace(call12, "", 1)
s = s.replace(reader12, "        ConfigReader configReader = ConfigReader.load();\n", 1)
s = s.replace(marker12,
        "        LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);\n"
        "        new MainHook().install(classLoader);\n", 1)
s = s.replace(dock12, "        DockBottomGeometryHook.install(classLoader);\n", 1)
p.write_text(s)
print("device glass prep applied")
