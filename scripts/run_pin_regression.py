"""Run the opt-in PIN regression only on a disposable API 26 emulator."""
import argparse
from pathlib import Path
import secrets
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--disposable-emulator", action="store_true", required=True)
parser.parse_args()


def adb(*arguments, timeout=120):
    return subprocess.run(["adb", *arguments], capture_output=True, text=True, timeout=timeout)


sdk = adb("shell", "getprop", "ro.build.version.sdk").stdout.strip()
if sdk != "26":
    print("::notice title=Device PIN regression::Opt-in legacy credential regression runs on API 26; skipped on this matrix entry.")
    sys.exit(0)
if adb("shell", "getprop", "ro.kernel.qemu").stdout.strip() != "1":
    sys.exit("Refusing to change credentials on a non-emulator device")

pin = str(100000 + secrets.randbelow(900000))
setup = adb("shell", "locksettings", "set-pin", pin)
if setup.returncode or "set to" not in setup.stdout.lower():
    sys.exit("Could not configure disposable emulator credential; existing credentials were not supplied")
try:
    result = adb("shell", "am", "instrument", "-w", "-r", "-e", "class",
                 "org.litespace.browser.ForegroundLockDeviceTest", "-e", "testDevicePin", pin,
                 "org.litespace.browser.test/androidx.test.runner.AndroidJUnitRunner", timeout=360)
finally:
    cleanup = adb("shell", "locksettings", "clear", "--old", pin)

report = Path("app/build/outputs/androidTest-results/pin-regression-api26.txt")
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(result.stdout + result.stderr, encoding="utf-8")
print(result.stdout)
success = (result.returncode == 0 and "OK (1 test)" in result.stdout
           and "INSTRUMENTATION_STATUS_CODE: 0" in result.stdout
           and "INSTRUMENTATION_STATUS_CODE: -4" not in result.stdout
           and "FAILURES!!!" not in result.stdout and cleanup.returncode == 0 and "cleared" in cleanup.stdout.lower())
if not success:
    details = (result.stdout + result.stderr)[-8000:].replace(pin, "[test PIN]")
    details = details.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::error title=Device PIN regression::Credential reopen regression failed. " + details)
    sys.exit(1)
print("::notice title=Device PIN regression::1 passed; real Android credential confirmation before and after Home; original WebView preserved.")
