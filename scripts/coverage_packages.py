#!/usr/bin/env python3
"""Print per-package coverage breakdown from a Kover XML report."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path

DEFAULT_REPORT = Path("build/reports/kover/report.xml")


def main(argv: list[str]) -> int:
    report_path = Path(argv[1]) if len(argv) > 1 else DEFAULT_REPORT
    if not report_path.exists():
        print(f"error: coverage report not found at {report_path}", file=sys.stderr)
        return 1

    root = ET.parse(report_path).getroot()
    pkgs = [
        (p.get("name"), int(c.get("covered")), int(c.get("missed")))
        for p in root.findall("package")
        for c in p.findall("counter")
        if c.get("type") == "INSTRUCTION"
    ]
    pkgs.sort(key=lambda x: -x[2])

    print(f"{'package':<55} {'cov%':>6} {'covered':>9} {'missed':>9} {'total':>9}")
    for name, covered, missed in pkgs:
        total = covered + missed
        pct = (covered / total * 100) if total else 0.0
        print(f"{name:<55} {pct:6.1f} {covered:9d} {missed:9d} {total:9d}")

    total_covered = sum(p[1] for p in pkgs)
    total_missed = sum(p[2] for p in pkgs)
    grand_total = total_covered + total_missed
    overall_pct = (total_covered / grand_total * 100) if grand_total else 0.0
    print(
        f"\nOVERALL: {overall_pct:.2f}% "
        f"({total_covered}/{grand_total} instructions, {total_missed} missed)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
