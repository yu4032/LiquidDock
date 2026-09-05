from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def replace_exact(path: Path, old: str, new: str, expected: int = 1) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"{path}: expected {expected} matches, got {count}: {old[:140]!r}")
    path.write_text(text.replace(old, new, expected))


maml = ROOT / "LauncherMamlBackgroundRuleExecutor.java"
replace_exact(
    maml,
    "        Claim previous = CLAIMS.get(host);\n"
    "        if (previous != null && previous.matches(root, resolved)) {\n"
    "            for (Object target : resolved) {\n"
    "                if (!invokeOptionalMutation(target, \"show\", false)) {\n"
    "                    restore(previous);\n"
    "                    CLAIMS.remove(host);\n"
    "                    MainHook.log(LOG_TAG + identityText\n"
    "                            + \" rule=\" + rule.id()\n"
    "                            + \" targets=\" + elementNames\n"
    "                            + \" targetFound=true mutationFailed=true suppressed=false\");\n"
    "                    return;\n"
    "                }\n"
    "            }\n"
    "            MainHook.log(LOG_TAG + identityText\n"
    "                    + \" rule=\" + rule.id()\n"
    "                    + \" targets=\" + elementNames\n"
    "                    + \" targetFound=true suppressed=\" + allHidden(resolved));\n"
    "            return;\n"
    "        }",
    "        Claim previous = CLAIMS.get(host);\n"
    "        if (previous != null && previous.matches(root, resolved)) {\n"
    "            AtomicMutationClaimState mutation =\n"
    "                    new AtomicMutationClaimState(resolved.size());\n"
    "            if (!mutation.beginIfFullyResolved(resolved.size())) return;\n"
    "            for (Object target : resolved) {\n"
    "                AtomicMutationClaimState.Decision decision = mutation.onMutationResult(\n"
    "                        invokeOptionalMutation(target, \"show\", false));\n"
    "                if (decision.rollbackCount > 0) {\n"
    "                    restore(previous);\n"
    "                    CLAIMS.remove(host);\n"
    "                    MainHook.log(LOG_TAG + identityText\n"
    "                            + \" rule=\" + rule.id()\n"
    "                            + \" targets=\" + elementNames\n"
    "                            + \" targetFound=true mutationFailed=true suppressed=false\");\n"
    "                    return;\n"
    "                }\n"
    "            }\n"
    "            MainHook.log(LOG_TAG + identityText\n"
    "                    + \" rule=\" + rule.id()\n"
    "                    + \" targets=\" + elementNames\n"
    "                    + \" targetFound=true suppressed=\" + allHidden(resolved));\n"
    "            return;\n"
    "        }",
)
replace_exact(
    maml,
    "        int appliedCount = 0;\n"
    "        for (Object target : resolved) {\n"
    "            appliedCount++;\n"
    "            if (!invokeOptionalMutation(target, \"show\", false)) {\n"
    "                restoreElements(elementClaims, appliedCount);\n"
    "                MainHook.log(LOG_TAG + identityText\n"
    "                        + \" rule=\" + rule.id()\n"
    "                        + \" targets=\" + elementNames\n"
    "                        + \" targetFound=true mutationFailed=true suppressed=false\");\n"
    "                return;\n"
    "            }\n"
    "        }\n"
    "        CLAIMS.put(host, new Claim(root, elementClaims));",
    "        AtomicMutationClaimState mutation = new AtomicMutationClaimState(resolved.size());\n"
    "        if (!mutation.beginIfFullyResolved(resolved.size())) return;\n"
    "        for (Object target : resolved) {\n"
    "            AtomicMutationClaimState.Decision decision = mutation.onMutationResult(\n"
    "                    invokeOptionalMutation(target, \"show\", false));\n"
    "            if (decision.rollbackCount > 0) {\n"
    "                restoreElements(elementClaims, decision.rollbackCount);\n"
    "                MainHook.log(LOG_TAG + identityText\n"
    "                        + \" rule=\" + rule.id()\n"
    "                        + \" targets=\" + elementNames\n"
    "                        + \" targetFound=true mutationFailed=true suppressed=false\");\n"
    "                return;\n"
    "            }\n"
    "            if (decision.commitClaim) {\n"
    "                CLAIMS.put(host, new Claim(root, elementClaims));\n"
    "            }\n"
    "        }",
)

selection = ROOT / "LauncherWidgetComponentSelectionExecutor.java"
replace_exact(
    selection,
    "            boolean originalShow = readBooleanField(target, \"mShow\", true);\n"
    "            if (invokeOptionalMutation(target, \"show\", false)) {\n"
    "                claims.add(new MamlClaim(target, originalShow));\n"
    "            }",
    "            boolean originalShow = readBooleanField(target, \"mShow\", true);\n"
    "            AtomicMutationClaimState mutation = new AtomicMutationClaimState(1);\n"
    "            if (!mutation.beginIfFullyResolved(1)) continue;\n"
    "            AtomicMutationClaimState.Decision decision = mutation.onMutationResult(\n"
    "                    invokeOptionalMutation(target, \"show\", false));\n"
    "            if (decision.commitClaim) {\n"
    "                claims.add(new MamlClaim(target, originalShow));\n"
    "            }",
)

for path in (maml, selection):
    text = path.read_text()
    if "AtomicMutationClaimState" not in text:
        raise SystemExit(f"{path}: state machine wiring missing")
