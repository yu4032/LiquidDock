#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
p = root / "src/main/java/com/hellovoid/liquiddock/ModuleMain.java"
s = p.read_text()
marker12 = "            LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);\n"
reader12 = "            ConfigReader configReader = ConfigReader.load();\n"
call12 = "            new MainHook().install(classLoader);\n"
if marker12 not in s or reader12 not in s or call12 not in s:
    raise SystemExit("ModuleMain runtime/MainHook anchor missing")
# The experiment patch was authored against an older 8-space indentation. Normalize only its
# three anchor lines; Java semantics are unchanged. MainHook is moved immediately after the
# config snapshot so the existing patch can insert GlassRuntimeState before it.
s = s.replace(call12, "", 1)
s = s.replace(reader12, "        ConfigReader configReader = ConfigReader.load();\n", 1)
s = s.replace(marker12,
        "        LiquidDockConfig runtimeConfig = LiquidDockConfig.from(configReader);\n"
        "        new MainHook().install(classLoader);\n", 1)
p.write_text(s)
print("device glass prep applied")
