#!/usr/bin/env python3
"""End-to-end smoke test for the self-built Bingo engine server bundle."""
from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import threading
import time
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
BUNDLE = DIST / "bingo-26.3-server.zip"
SERVER = DIST / "server-test"
TIMEOUT = 420


def prepare() -> Path:
    if SERVER.exists():
        shutil.rmtree(SERVER)
    SERVER.mkdir(parents=True)
    with zipfile.ZipFile(BUNDLE) as bundle:
        bundle.extractall(SERVER)
    (SERVER / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (SERVER / "server.properties").write_text(
        "level-name=world\nonline-mode=false\nview-distance=6\nsimulation-distance=4\n"
        "max-players=8\nmotd=smoke-test\n", encoding="utf-8")
    cached_server = DIST / "cache" / "minecraft-server-26.3.jar"
    if cached_server.exists():
        target = SERVER / ".fabric" / "server" / "26.3-server.jar"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(cached_server, target)
    launchers = list(SERVER.glob("fabric-server-*.jar"))
    if not launchers:
        sys.exit("No fabric server launcher in bundle")
    return launchers[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keep", action="store_true")
    args = parser.parse_args()

    launcher = prepare()
    log_path = DIST / "smoke-test.log"
    output: list[str] = []
    done = threading.Event()

    proc = subprocess.Popen(
        ["java", "-Xms1G", "-Xmx2G", "-jar", launcher.name, "nogui"],
        cwd=SERVER, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT, text=True, bufsize=1,
    )

    def reader():
        assert proc.stdout is not None
        with log_path.open("w", encoding="utf-8") as log:
            for line in proc.stdout:
                log.write(line)
                log.flush()
                output.append(line)
                if "Done (" in line:
                    done.set()

    threading.Thread(target=reader, daemon=True).start()

    def send(command: str, delay: float = 2.0):
        print(f"> {command}", flush=True)
        assert proc.stdin is not None
        proc.stdin.write(command + "\n")
        proc.stdin.flush()
        time.sleep(delay)

    if not done.wait(TIMEOUT):
        proc.kill()
        sys.exit(f"Server did not start within {TIMEOUT}s (see {log_path})")

    time.sleep(5)
    send("bingo info")
    send("bingo stack")
    send("bingo kit player", 1.5)
    send("bingo spawn minecraft:the_end", 1.5)
    send("bingo spawn minecraft:overworld", 1.5)
    send("bingo size 7", 1.5)
    send("bingo fog on", 1.5)
    send("bingo difficulty hard", 1.5)
    send("bingo start force", 12)
    send("bingo info")
    send("bingo event mobs")
    send("bingo supply")
    send("bingo end")
    send("bingo start force", 12)
    send("bingo weather rain", 1.5)
    send("bingo daylight night", 1.5)
    send("bingo board 1 difficulty extreme", 1.5)
    send("bingo board", 1.5)
    send("bingo info")
    send("stop", 1.0)

    try:
        proc.wait(timeout=120)
    except subprocess.TimeoutExpired:
        proc.kill()

    text = "".join(output)
    checks = {
        "server started": "Done (" in text,
        "engine loaded": "[Bingo] Bingo! 26.3 engine loaded" in text,
        "objective pools": "Loaded 1503 item and 121 advancement objectives" in text,
        "mission registry": re.search(r"registry ours=126 vanilla=0", text) is not None,
        "128 stack": re.search(r"Stack limit: 128 \| test stack: 128", text) is not None,
        "spawn command": "Spawn dimension set to" in text,
        "kit command": "Starting kit set to player" in text,
        "random arena recreated": text.count("New arena seed") >= 2,
        "round playing": "Phase: PLAYING" in text,
        "survives events": "Exception in thread" not in text,
        "no mixin errors": "Mixin apply failed" not in text,
        "no data pack errors": "Failed to load advancement" not in text,
    }
    print()
    failed = False
    for name, ok in checks.items():
        print(("PASS " if ok else "FAIL ") + name)
        failed |= not ok

    errors = [line for line in output if line.startswith("ERROR") or "Exception" in line]
    if errors:
        print("\nErrors in log:")
        for line in errors[:20]:
            print("  " + line.rstrip())

    if not args.keep:
        shutil.rmtree(SERVER, ignore_errors=True)
    print(f"\nlog: {log_path}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
