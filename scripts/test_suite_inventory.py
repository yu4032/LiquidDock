#!/usr/bin/env python3
"""Inventory JUnit source annotations and actual Gradle JUnit XML executions.

The two counts are intentionally separate: parameterized/inherited tests can
execute more cases than the number of @Test annotations in the source tree.
This script does not claim to measure JaCoCo coverage or historical flakiness.
"""
import argparse
import csv
import json
import re
from pathlib import Path
import xml.etree.ElementTree as ET

TEST_ROOTS = ("src/test", "src/androidTest", "prismal/src/test", "prismal/src/androidTest")
RESULT_ROOTS = ("build/test-results", "prismal/build/test-results")
TEST_ANNOTATION = re.compile(r"(?m)^[ \t]*@(?:(?:org\.junit(?:\.jupiter\.api)?)\.)?Test\b")
PROTECTED_NAMES = ("PassBlur", "Prismal", "Workstation", "Config", "Hook", "Animation", "Rotation")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, default=Path("build/reports/test-inventory"))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for relative in TEST_ROOTS:
        source_root = Path(relative)
        if not source_root.is_dir():
            continue
        for path in sorted(source_root.rglob("*")):
            if not path.is_file() or path.suffix not in (".java", ".kt"):
                continue
            source = path.read_text(encoding="utf-8")
            annotations = len(TEST_ANNOTATION.findall(source))
            rows.append({
                "path": path.as_posix(),
                "loc": len(source.splitlines()),
                "test_annotations": annotations,
                "production_source_reference": any(
                    marker in source for marker in ("src/main/", "build.gradle", "settings.gradle")
                ),
                "protected_area_hint": any(name in path.name for name in PROTECTED_NAMES),
            })

    with (args.output_dir / "source-inventory.csv").open("w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=(
            "path", "loc", "test_annotations", "production_source_reference", "protected_area_hint"
        ))
        writer.writeheader()
        writer.writerows(rows)

    xml_paths = sorted(
        path for root in RESULT_ROOTS if Path(root).is_dir()
        for path in Path(root).rglob("TEST-*.xml")
    )
    executed = failures = skipped = 0
    case_duration_s = 0.0
    for path in xml_paths:
        root = ET.parse(path).getroot()
        for case in root.iter("testcase"):
            executed += 1
            failures += any(case.find(tag) is not None for tag in ("failure", "error"))
            skipped += case.find("skipped") is not None
            case_duration_s += float(case.get("time", "0"))

    summary = {
        "source_files": len(rows),
        "test_named_source_files": sum(
            Path(row["path"]).stem.endswith("Test") for row in rows
        ),
        "source_test_annotations": sum(row["test_annotations"] for row in rows),
        "production_source_reference_files": sum(
            row["production_source_reference"] for row in rows
        ),
        "junit_xml_reports": len(xml_paths),
        "executed_testcases": executed if xml_paths else None,
        "failed_testcases": failures if xml_paths else None,
        "skipped_testcases": skipped if xml_paths else None,
        "summed_case_duration_seconds": round(case_duration_s, 3) if xml_paths else None,
        "notes": [
            "Source @Test annotations are not equivalent to executed JUnit cases.",
            "JUnit XML results describe this run only, not historical failure rates.",
            "No JaCoCo coverage or unique coverage contribution is measured.",
        ],
    }
    (args.output_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print("Test inventory: " + json.dumps(summary, ensure_ascii=False))


if __name__ == "__main__":
    main()
