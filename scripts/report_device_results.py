"""Expose real JUnit device results in GitHub annotations and the job summary."""
import os
from pathlib import Path
import xml.etree.ElementTree as ET


def escape(value):
    return value.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


passed = failed = skipped = 0
details = []
reports = list(Path("app/build/outputs/androidTest-results").rglob("TEST-*.xml"))
for report in reports:
    root = ET.parse(report).getroot()
    for case in root.iter("testcase"):
        label = f"{case.get('classname')}.{case.get('name')}"
        failures = list(case.findall("failure")) + list(case.findall("error"))
        if failures:
            failed += 1
            message = failures[0].get("message") or failures[0].text or "No failure detail"
            print(f"::error title=Device test failed::{escape(label + ': ' + message[:4000])}")
        elif case.find("skipped") is not None:
            skipped += 1
            details.append(label)
        else:
            passed += 1

summary = f"{passed} passed, {failed} failed, {skipped} skipped ({len(reports)} JUnit reports)"
print(f"::notice title=Android instrumentation results::{summary}")
for label in details:
    print(f"::notice title=Test skipped::{escape(label)}")
if not reports:
    print("::warning title=Device results unavailable::No JUnit reports; inspect emulator/build startup logs.")
if os.environ.get("GITHUB_STEP_SUMMARY"):
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
        output.write(f"### Android instrumentation\n\n{summary}\n\n")
        if details:
            output.write("Skipped:\n\n" + "\n".join(f"- `{label}`" for label in details) + "\n")
