#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"
s = p.read_text()
marker = "            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);\n"
call = "            new MainHook().install(classLoader);\n"
if marker not in s:
    raise SystemExit("ModuleMain runtimeConfig anchor missing")
if call not in s:
    raise SystemExit("ModuleMain MainHook anchor missing")
# The main experiment patch expects MainHook immediately after runtimeConfig. This move is
# behavior-neutral for the following grid-profile calculations and gives the patch a stable anchor.
s = s.replace(call, "", 1)
s = s.replace(marker, marker + call, 1)
p.write_text(s)
print("device glass prep applied")
