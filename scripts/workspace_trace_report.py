#!/usr/bin/env python3
"""Summarize existing LD.Workspace Perfetto markers without changing runtime behavior.

Usage:
    python scripts/workspace_trace_report.py stable.perfetto-trace
    python scripts/workspace_trace_report.py candidate.perfetto-trace --reference baseline.perfetto-trace

Requires: python -m pip install perfetto
"""

import argparse
from pathlib import Path
import sys

SLICES = (
    "LD.Workspace.SceneSync",
    "LD.Workspace.DragNodes",
    "LD.Workspace.StaticNodes",
    "LD.Workspace.SourceReconcile",
)
COUNTERS = (
    "LD.Workspace.DragCandidates",
    "LD.Workspace.DragGeometryReads",
    "LD.Workspace.StaticCandidates",
    "LD.Workspace.StaticGeometryReads",
)


def percentile(values, fraction):
    """Linear interpolation, independent of NumPy or trace-processor SQL functions."""
    if not values:
        return float("nan")
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    left = int(position)
    right = min(len(ordered) - 1, left + 1)
    return ordered[left] + (ordered[right] - ordered[left]) * (position - left)


def describe(values):
    return {
        "n": len(values),
        "p50": percentile(values, 0.50),
        "p95": percentile(values, 0.95),
        "p99": percentile(values, 0.99),
    }


def analyze(path):
    try:
        from perfetto.trace_processor import TraceProcessor
    except ImportError as error:
        raise RuntimeError(
            "Perfetto TraceProcessor is missing: python -m pip install perfetto"
        ) from error

    slices = {name: [] for name in SLICES}
    counters = {name: [] for name in COUNTERS}
    with TraceProcessor(trace=str(path)) as tp:
        # dur < 0 means a truncated/unclosed slice, not a measured time.
        for row in tp.query(
            "SELECT name, dur FROM slice "
            "WHERE name LIKE 'LD.Workspace.%' AND dur >= 0"
        ):
            if row.name in slices:
                slices[row.name].append(float(row.dur) / 1000.0)  # ns -> us
        for row in tp.query(
            "SELECT t.name AS name, c.value AS value "
            "FROM counter c JOIN counter_track t ON t.id = c.track_id "
            "WHERE t.name LIKE 'LD.Workspace.%' ORDER BY c.ts"
        ):
            if row.name in counters:
                counters[row.name].append(float(row.value))

    if not slices[SLICES[0]]:
        raise RuntimeError(
            f"No {SLICES[0]} slices in {path}; verify com.miui.home atrace "
            "and that the trace actually contains Workspace frames."
        )
    results = {name: describe(samples) for name, samples in slices.items()}
    stats = {name: describe(samples) for name, samples in counters.items()}
    candidates = counters["LD.Workspace.StaticCandidates"]
    reads = counters["LD.Workspace.StaticGeometryReads"]
    # Each SceneSync publishes the two counters once in order. If a trace is
    # incomplete, do not combine values from unmatched time windows.
    ratio = None
    if len(candidates) == len(reads) and candidates and sum(candidates) > 0:
        ratio = sum(reads) / sum(candidates)
    return results, stats, ratio


def fmt(value):
    return f"{value:9.1f}"


def print_report(label, report):
    slices, counters, ratio = report
    print(f"\n{label}")
    print("Stage (microseconds)             N         p50       p95       p99")
    for name in SLICES:
        s = slices[name]
        print(f"{name[13:]:27} {s['n']:7} {fmt(s['p50'])} {fmt(s['p95'])} {fmt(s['p99'])}")
    print("Counter (per pre-draw)            N         p50       p95       p99")
    for name in COUNTERS:
        s = counters[name]
        print(f"{name[13:]:27} {s['n']:7} {fmt(s['p50'])} {fmt(s['p95'])} {fmt(s['p99'])}")
    if ratio is None:
        print("Static capture/candidate ratio: unavailable (incomplete or zero counters)")
    else:
        print(f"Static capture/candidate ratio: {ratio:.1%}")
    if slices[SLICES[0]]["n"] < 100:
        print("WARNING: fewer than 100 frames; do not treat this as a reliable performance baseline.")
    if any(counters[name]["n"] == 0 for name in COUNTERS):
        print("WARNING: missing counters; check trace attribution and buffer size.")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path, help="Workspace Perfetto trace")
    parser.add_argument("--reference", type=Path, help="matched baseline trace (same scenario/device)")
    args = parser.parse_args(argv)
    for path in (args.trace, args.reference):
        if path is not None and not path.is_file():
            parser.error(f"trace not found: {path}")
    try:
        reference = analyze(args.reference) if args.reference is not None else None
        candidate = analyze(args.trace)
    except Exception as error:
        print(f"Trace analysis failed: {error}", file=sys.stderr)
        return 1
    if reference is not None:
        print_report(f"REFERENCE: {args.reference}", reference)
    print_report(f"TRACE: {args.trace}", candidate)
    if reference is not None:
        print("\nCandidate relative to reference (negative = faster):")
        for name in SLICES:
            old = reference[0][name]["p95"]
            new = candidate[0][name]["p95"]
            if old > 0:
                print(f"  {name}: p95 {(new / old - 1) * 100:+.1f}%")
        print("A difference is not evidence of causation without matched runs and device checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
