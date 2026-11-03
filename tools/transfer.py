#!/usr/bin/env python3
"""Transfer Glass TV exchange files (private debug or app-specific media folder); never changes packages or ADB servers."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

PACKAGE = "de.glasslauncher.tv"
SAFE_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z")
BACKUP_NAME = re.compile(r"glass-tv-settings-[a-f0-9]{8}\.json\Z")
LIMITS = {".jpg": 32 << 20, ".jpeg": 32 << 20, ".png": 32 << 20, ".webp": 32 << 20, ".mp4": 128 << 20, ".json": 256 << 10}


def adb(device, *args, **kwargs):
    return subprocess.run(["/usr/bin/adb", "-s", device, *args], check=True, **kwargs)



def storage(device):
    result = subprocess.run(["/usr/bin/adb", "-s", device, "shell", "run-as", PACKAGE, "id"], capture_output=True)
    return "private" if result.returncode == 0 else "media"


def directory(backend, kind):
    return "files/exchange/" + kind if backend == "private" else "/sdcard/Android/media/" + PACKAGE + "/exchange/" + kind


def scoped(device, backend, *args, **kwargs):
    return adb(device, "exec-out", *( ["run-as", PACKAGE] if backend == "private" else []), *args, **kwargs)

def import_file(device, source):
    source = Path(source).expanduser().resolve(strict=True)
    if not source.is_file() or not SAFE_NAME.fullmatch(source.name):
        raise ValueError("Use a simple file name with letters, digits, dots, dashes or underscores")
    maximum = LIMITS.get(source.suffix.lower())
    if maximum is None or not 0 < source.stat().st_size <= maximum:
        raise ValueError("Unsupported type, empty file or size limit exceeded")
    backend = storage(device)
    root = directory(backend, "imports")
    scoped(device, backend, "mkdir", "-p", root)
    destination = root + "/" + source.name
    if backend == "media":
        adb(device, "push", str(source), destination)
    else:
        command = shlex.quote("cat > " + shlex.quote(destination))
        with source.open("rb") as stream:
            adb(device, "shell", "-T", "run-as", PACKAGE, "sh", "-c", command, stdin=stream)
    print(f"Staged {source.name}; choose it in Glass TV to validate and import")


def backup_files(device, destination):
    folder = Path(destination).expanduser().resolve()
    folder.mkdir(parents=True, exist_ok=True)
    backend = storage(device)
    root = directory(backend, "exports")
    result = scoped(device, backend, "ls", "-1", root, capture_output=True, text=True)
    names = [line.strip() for line in result.stdout.splitlines() if BACKUP_NAME.fullmatch(line.strip())]
    if not names:
        raise ValueError("No exported backups; export settings in Glass TV first")
    for name in names:
        result = scoped(device, backend, "cat", root + "/" + name, capture_output=True)
        data = result.stdout
        if not 0 < len(data) <= 256 << 10:
            raise ValueError("Invalid backup size")
        document = json.loads(data.decode("utf-8"))
        if not isinstance(document, dict) or document.get("version") != 1:
            raise ValueError("Unsupported backup document")
        target = folder / name
        if target.exists():
            if target.read_bytes() != data:
                raise ValueError(f"Refusing to replace a different existing file: {target}")
        else:
            with target.open("xb") as output:
                output.write(data)
        print(f"Saved {target} ({len(data)} bytes, SHA256 {hashlib.sha256(data).hexdigest()})")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="Existing authorized ADB device serial")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("import", help="Stage a local file for explicit in-app import").add_argument("source")
    commands.add_parser("backup", help="Copy exported JSON backups permanently onto this computer").add_argument("destination")
    args = parser.parse_args()
    try:
        if args.command == "import":
            import_file(args.device, args.source)
        else:
            backup_files(args.device, args.destination)
    except (OSError, ValueError, subprocess.CalledProcessError) as failure:
        print(f"Transfer failed: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
