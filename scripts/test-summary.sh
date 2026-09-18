#!/usr/bin/env bash
# Renders target/surefire-reports/TEST-*.xml as a GitHub Step Summary: a totals
# table, then - for every failed or skipped test - its message and a link to
# this run's full details (check annotations, artifacts, and, once published,
# the Allure report).
set -euo pipefail

RESULTS_DIR="${1:-target/surefire-reports}"
RUN_URL="${GITHUB_SERVER_URL:-}/${GITHUB_REPOSITORY:-}/actions/runs/${GITHUB_RUN_ID:-}"

# The Allure report is only built and published on master (see api-tests.yml), and its
# Pages URL is predictable, so link to it even though it hasn't been published yet at
# this point in the job - by the time anyone reads the summary, the later steps have run.
ALLURE_URL=""
if [ "${GITHUB_REF_NAME:-}" = "master" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
  owner="${GITHUB_REPOSITORY%%/*}"
  repo="${GITHUB_REPOSITORY##*/}"
  owner_lower="$(printf '%s' "$owner" | tr '[:upper:]' '[:lower:]')"
  ALLURE_URL="https://${owner_lower}.github.io/${repo}/"
fi

if ! compgen -G "${RESULTS_DIR}/TEST-*.xml" > /dev/null; then
  echo "No surefire reports found under ${RESULTS_DIR}."
  exit 0
fi

python3 - "$RESULTS_DIR" "$RUN_URL" "$ALLURE_URL" <<'PY'
import glob
import html
import sys
import xml.etree.ElementTree as ET

sys.stdout.reconfigure(encoding="utf-8")

results_dir, run_url, allure_url = sys.argv[1], sys.argv[2], sys.argv[3]

total = failures = errors = skipped = 0
failed_tests = []
skipped_tests = []


def first_line(text):
    text = (text or "").strip()
    return text.splitlines()[0] if text else ""


for path in sorted(glob.glob(f"{results_dir}/TEST-*.xml")):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue

    total += int(root.get("tests", 0) or 0)
    failures += int(root.get("failures", 0) or 0)
    errors += int(root.get("errors", 0) or 0)
    skipped += int(root.get("skipped", 0) or 0)

    classname = root.get("name", "")
    for testcase in root.findall("testcase"):
        full_name = f"{classname}#{testcase.get('name', '')}"

        failure_el = testcase.find("failure")
        error_el = testcase.find("error")
        skipped_el = testcase.find("skipped")

        if failure_el is not None or error_el is not None:
            el = failure_el if failure_el is not None else error_el
            message = first_line(el.get("message") or el.text)
            failed_tests.append((full_name, message))
        elif skipped_el is not None:
            message = first_line(skipped_el.get("message") or skipped_el.text)
            skipped_tests.append((full_name, message))

passed = max(total - failures - errors - skipped, 0)

print("| Total | Passed | Failed | Skipped |")
print("|---|---|---|---|")
print(f"| {total} | {passed} | {failures + errors} | {skipped} |")
print()

links = [f"[Run details]({run_url})"]
if allure_url:
    links.insert(0, f"[Full Allure report]({allure_url})")
print(" · ".join(links))
print()


def render(title, emoji, tests):
    if not tests:
        return
    print(f"### {emoji} {title} ({len(tests)})")
    for name, message in tests:
        line = f"- **{html.escape(name)}**"
        if message:
            line += f" — {html.escape(message)}"
        line += f" ([details]({run_url}))"
        print(line)
    print()


render("Failed", "❌", failed_tests)
render("Skipped", "⏭️", skipped_tests)

if total and not failed_tests and not skipped_tests:
    print("✅ All tests passed.")
PY
