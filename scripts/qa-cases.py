#!/usr/bin/env python3
"""B-114's check: the QA catalogue in docs/qa/ is a list of cases a person can walk, each one held by something.

Standard library only. `make check` runs it under `gate`, so CI does too. It refuses, naming the file and the line:

- a case heading (`### …` in an area file) that is not `TC-<AREA>-NN — <title>`, a duplicate id, and an id whose
  area is not the file's (one area per file, the area of its first case);
- a case without each of Who, Width, Preconditions, Steps (at least one numbered step), Expected and Coverage,
  a field given twice, and a field the format does not have (a misspelt one would otherwise be read as no field);
- a width that is not one of the widths the catalogue walks;
- a coverage entry that names a test class no test source set has, a test name that class does not declare, a
  golden with no PNG, a script that is not there, or a bare `manual` (a manual case says why no test holds it);
- a «Decided in» naming a backlog item that has no file;
- in a run (`docs/qa/runs/<date>-<release>.md`): a case the catalogue does not have, a result that is not pass,
  fail, blocked or not run, and a fail that names no backlog item.

The coverage entries, separated by `;`:

    `ClassName` «the test's name»                 a test, found by its class file and its backticked name
    `server:ClassName` «the test's name»          the module, when two modules have a class of that name
    golden `Name`                                 composeApp/src/desktopTest/snapshots/Name.png, held by viddikVerify
    script `scripts/name.sh`                      a script that runs on every pull request or on the stand
    manual: why no test can hold it              last, as its reason may hold a `;` of its own

    scripts/qa-cases.py [--root DIR] [--summary]

`--summary` prints the cases per area and how each is covered, for the item's findings.
"""

import argparse
import os
import re
import sys
from collections import OrderedDict, defaultdict

FIELDS = ["Who", "Width", "Preconditions", "Steps", "Expected", "Decided in", "Coverage"]
REQUIRED = ["Who", "Width", "Preconditions", "Steps", "Expected", "Coverage"]
WIDTHS = {"375", "390", "800", "1024", "1280", "1366", "1440", "any", "n/a"}
RESULTS = {"pass", "fail", "blocked", "not run"}
MODULES = ["server", "composeApp", "e2e", "shared"]

CASE_ID = re.compile(r"^TC-([A-Z]+)-(\d{2,3})$")
HEADING = re.compile(r"^###\s+(.*?)\s*$")
CASE_HEADING = re.compile(r"^(TC-[A-Z]+-\d{2,3})\s+[—-]\s+\S")
FIELD = re.compile(r"^- \*\*([^*:]+):\*\*(.*)$")
NUMBERED = re.compile(r"^\s*\d+\.\s+\S")
ITEM = re.compile(r"\bB-(\d+)\b")
TEST_ENTRY = re.compile(r"^`(?:([A-Za-z]+):)?([A-Z][A-Za-z0-9]*)`\s*«([^»]+)»$")
GOLDEN_ENTRY = re.compile(r"^golden\s+`([A-Za-z0-9_]+)`$")
SCRIPT_ENTRY = re.compile(r"^script\s+`([^`]+)`$")
MANUAL_ENTRY = re.compile(r"^manual\b\s*[:—-]?\s*(.*)$", re.S)
RUN_NAME = re.compile(r"^\d{4}-\d{2}-\d{2}-[A-Za-z0-9._-]+\.md$")


class Problems:
    def __init__(self):
        self.lines = []

    def add(self, path, line, message):
        self.lines.append(f"{path}:{line}: {message}")


def test_classes(root):
    """Every Kotlin file of a test source set, by class name: [(module, path)]."""
    found = defaultdict(list)
    for module in MODULES:
        src = os.path.join(root, module, "src")
        if not os.path.isdir(src):
            continue
        for set_name in os.listdir(src):
            if not (set_name == "test" or set_name.endswith("Test")):
                continue
            for base, _, files in os.walk(os.path.join(src, set_name)):
                for name in files:
                    if name.endswith(".kt"):
                        found[name[:-3]].append((module, os.path.join(base, name)))
    return found


def backlog_items(root):
    items = set()
    folder = os.path.join(root, "docs", "backlog")
    for name in os.listdir(folder) if os.path.isdir(folder) else []:
        m = re.match(r"^B-(\d+)-.*\.md$", name)
        if m:
            items.add(int(m.group(1)))
    return items


def parse_cases(path, rel, problems):
    """The cases of one area file: [{id, line, fields: {name: (line, text)}}]."""
    cases = []
    current = None
    field = None
    in_fence = False
    with open(path, encoding="utf-8") as f:
        lines = f.read().split("\n")
    for number, line in enumerate(lines, start=1):
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        heading = HEADING.match(line)
        if heading:
            text = heading.group(1)
            m = CASE_HEADING.match(text)
            if not m:
                problems.add(rel, number, f"a case heading without an id: «### {text}» — write «### TC-<AREA>-NN — <title>»")
                current = {"id": None, "line": number, "fields": {}}
            else:
                current = {"id": m.group(1), "line": number, "fields": {}}
            cases.append(current)
            field = None
            continue
        if line.startswith("#"):
            current = None
            field = None
            continue
        if current is None:
            continue
        m = FIELD.match(line)
        if m:
            name, text = m.group(1).strip(), m.group(2).strip()
            if name not in FIELDS:
                problems.add(rel, number, f"{current['id'] or 'a case'}: «{name}» is not a field of a case ({', '.join(FIELDS)})")
                field = None
                continue
            if name in current["fields"]:
                problems.add(rel, number, f"{current['id'] or 'a case'}: «{name}» is given twice")
            current["fields"][name] = [number, text]
            field = name
            continue
        if field and (line.startswith(" ") or line.startswith("\t")) and line.strip():
            entry = current["fields"][field]
            entry[1] = (entry[1] + "\n" + line).strip("\n")
            continue
        if line.strip() == "":
            continue
        field = None
    return cases


def check_coverage(case, rel, line, text, classes, root, problems):
    kinds = set()
    entries = [e.strip() for e in text.replace("\n", " ").split(";") if e.strip()]
    # `manual: why` comes last and its reason may hold a `;` of its own.
    for i, entry in enumerate(entries):
        if MANUAL_ENTRY.match(entry) and entry.startswith("manual"):
            entries = entries[:i] + ["; ".join(entries[i:])]
            break
    if not entries:
        problems.add(rel, line, f"{case}: the coverage is empty — a test, a golden, a script or «manual: why»")
    for entry in entries:
        entry = re.sub(r"\s+", " ", entry)
        m = TEST_ENTRY.match(entry)
        if m:
            module, cls, name = m.group(1), m.group(2), m.group(3)
            where = classes.get(cls, [])
            if module:
                where = [w for w in where if w[0] == module]
            if not where:
                problems.add(rel, line, f"{case}: no test class `{(module + ':') if module else ''}{cls}` in any test source set")
                continue
            if len(where) > 1:
                problems.add(rel, line, f"{case}: `{cls}` is a test class of {', '.join(w[0] for w in where)} — name the module, `<module>:{cls}`")
                continue
            with open(where[0][1], encoding="utf-8") as f:
                source = f.read()
            if f"fun `{name}`" not in source:
                problems.add(rel, line, f"{case}: `{cls}` ({os.path.relpath(where[0][1], root)}) has no test «{name}»")
                continue
            kinds.add("test")
            continue
        m = GOLDEN_ENTRY.match(entry)
        if m:
            png = os.path.join(root, "composeApp", "src", "desktopTest", "snapshots", m.group(1) + ".png")
            if not os.path.isfile(png):
                problems.add(rel, line, f"{case}: no golden {m.group(1)}.png in composeApp/src/desktopTest/snapshots")
                continue
            kinds.add("golden")
            continue
        m = SCRIPT_ENTRY.match(entry)
        if m:
            if not os.path.isfile(os.path.join(root, m.group(1))):
                problems.add(rel, line, f"{case}: no script {m.group(1)}")
                continue
            kinds.add("script")
            continue
        m = MANUAL_ENTRY.match(entry)
        if m:
            if len(m.group(1).strip()) < 10:
                problems.add(rel, line, f"{case}: a manual case says why no test holds it — «manual: <why>»")
                continue
            kinds.add("manual")
            continue
        problems.add(rel, line, f"{case}: cannot read the coverage entry «{entry}»")
    return kinds


def check_catalogue(root, problems):
    qa = os.path.join(root, "docs", "qa")
    if not os.path.isdir(qa):
        problems.add("docs/qa", 0, "no catalogue: docs/qa/ is missing")
        return {}, OrderedDict()
    classes = test_classes(root)
    items = backlog_items(root)
    seen = {}
    areas = OrderedDict()
    files = sorted(n for n in os.listdir(qa) if n.endswith(".md") and n != "README.md")
    if not files:
        problems.add("docs/qa", 0, "no area files: the catalogue holds no case")
    for name in files:
        path = os.path.join(qa, name)
        rel = os.path.relpath(path, root)
        cases = parse_cases(path, rel, problems)
        if not cases:
            problems.add(rel, 1, "an area file with no case")
        area = None
        for case in cases:
            cid, line, fields = case["id"], case["line"], case["fields"]
            label = cid or f"the case at line {line}"
            if cid:
                if cid in seen:
                    problems.add(rel, line, f"{cid} is a duplicate id, first at {seen[cid]}")
                else:
                    seen[cid] = f"{rel}:{line}"
                code = CASE_ID.match(cid).group(1)
                if area is None:
                    area = code
                elif code != area:
                    problems.add(rel, line, f"{cid}: this file's cases are TC-{area}-NN")
            for required in REQUIRED:
                if required not in fields or not fields[required][1].strip():
                    problems.add(rel, line, f"{label}: no {required}")
            if "Steps" in fields and fields["Steps"][1].strip():
                if not any(NUMBERED.match(s) for s in fields["Steps"][1].split("\n")):
                    problems.add(rel, fields["Steps"][0], f"{label}: the steps are not numbered (1. … 2. …)")
            if "Width" in fields:
                for width in re.split(r"[,\s]+", fields["Width"][1].strip()):
                    width = width.strip()
                    if width and width not in WIDTHS:
                        problems.add(rel, fields["Width"][0], f"{label}: width «{width}» is not one of {', '.join(sorted(WIDTHS))}")
            if "Decided in" in fields:
                for n in ITEM.findall(fields["Decided in"][1]):
                    if int(n) not in items:
                        problems.add(rel, fields["Decided in"][0], f"{label}: B-{n} has no file in docs/backlog")
            kinds = set()
            if "Coverage" in fields and fields["Coverage"][1].strip():
                kinds = check_coverage(label, rel, fields["Coverage"][0], fields["Coverage"][1], classes, root, problems)
            if cid:
                areas.setdefault((area or "?", name), []).append((cid, kinds))
    return seen, areas


def check_runs(root, seen, problems):
    runs = os.path.join(root, "docs", "qa", "runs")
    count = 0
    if not os.path.isdir(runs):
        return 0
    items = backlog_items(root)
    for name in sorted(os.listdir(runs)):
        if not name.endswith(".md"):
            continue
        rel = os.path.relpath(os.path.join(runs, name), root)
        if not RUN_NAME.match(name):
            problems.add(rel, 1, "a run is named <yyyy-mm-dd>-<release>.md")
        count += 1
        with open(os.path.join(runs, name), encoding="utf-8") as f:
            for number, line in enumerate(f.read().split("\n"), start=1):
                if not line.startswith("| TC-"):
                    continue
                cells = [c.strip() for c in line.strip().strip("|").split("|")]
                cid = cells[0]
                if cid not in seen:
                    problems.add(rel, number, f"{cid} is not a case of the catalogue")
                result = cells[1].lower() if len(cells) > 1 else ""
                if result not in RESULTS:
                    problems.add(rel, number, f"{cid}: result «{result}» is not one of {', '.join(sorted(RESULTS))}")
                if result == "fail":
                    named = [int(n) for n in ITEM.findall(" ".join(cells[2:]))]
                    if not named:
                        problems.add(rel, number, f"{cid}: a fail names the backlog item it became (B-NN)")
                    for n in named:
                        if n not in items:
                            problems.add(rel, number, f"{cid}: B-{n} has no file in docs/backlog")
    return count


def summary(areas):
    total = defaultdict(int)
    print(f"{'area':<8} {'file':<28} {'cases':>5} {'auto':>5} {'mixed':>5} {'manual':>6}")
    for (area, name), cases in areas.items():
        auto = sum(1 for _, k in cases if k and "manual" not in k)
        mixed = sum(1 for _, k in cases if "manual" in k and len(k) > 1)
        manual = sum(1 for _, k in cases if k == {"manual"})
        print(f"{area:<8} {name:<28} {len(cases):>5} {auto:>5} {mixed:>5} {manual:>6}")
        for key, value in (("cases", len(cases)), ("auto", auto), ("mixed", mixed), ("manual", manual)):
            total[key] += value
    print(f"{'all':<8} {'':<28} {total['cases']:>5} {total['auto']:>5} {total['mixed']:>5} {total['manual']:>6}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--root", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
    parser.add_argument("--summary", action="store_true")
    args = parser.parse_args()
    root = os.path.abspath(args.root)
    problems = Problems()
    seen, areas = check_catalogue(root, problems)
    runs = check_runs(root, seen, problems)
    if args.summary:
        summary(areas)
    if problems.lines:
        print("\n".join(problems.lines), file=sys.stderr)
        print(f"qa-cases: {len(problems.lines)} problem(s)", file=sys.stderr)
        return 1
    print(f"qa-cases: {len(seen)} cases in {len(areas)} areas, {runs} run(s) recorded, every case held")
    return 0


if __name__ == "__main__":
    sys.exit(main())
