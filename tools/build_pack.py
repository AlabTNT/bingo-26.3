#!/usr/bin/env python3
"""Download the pinned dependency mods and assemble the client/server packs.

Outputs (dist/):
  Bingo-26.3-1.0.0.mrpack     Modrinth pack (client + server)
  bingo-26.3-server.zip       ready-to-run Fabric server bundle
  mods/                       plain mods folder (manual installs)

Usage:
  ./gradlew build && python3 tools/build_pack.py
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"
CACHE = DIST / "cache"
MANIFEST = json.loads((ROOT / "tools/pack_manifest.json").read_text(encoding="utf-8"))
LAUNCHER_VERSION = "1.1.2"
LAUNCHER_NAME = f"fabric-server-mc.{MANIFEST['minecraft']}-loader.{MANIFEST['loader']}-launcher.{LAUNCHER_VERSION}.jar"
LAUNCHER_URL = (
    f"https://meta.fabricmc.net/v2/versions/loader/{MANIFEST['minecraft']}/"
    f"{MANIFEST['loader']}/{LAUNCHER_VERSION}/server/jar"
)
OWN_MOD = ROOT / "build/libs" / "bingo-ourselves-1.0.0.jar"


def log(message: str):
    print(message, flush=True)


def download(url: str, target: Path, sha1: str | None = None, attempts: int = 6) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and (sha1 is None or sha1sum(target) == sha1):
        return target
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            log(f"  downloading {target.name} ({attempt}/{attempts})")
            with urllib.request.urlopen(url, timeout=180) as response, target.open("wb") as out:
                shutil.copyfileobj(response, out, length=1 << 20)
            if sha1 and sha1sum(target) != sha1:
                raise ValueError("sha1 mismatch")
            return target
        except Exception as error:  # noqa: BLE001 - retry any network error
            last_error = error
            time.sleep(min(10 * attempt, 30))
    raise RuntimeError(f"Could not download {url}: {last_error}")


def sha1sum(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def overrides_files() -> dict[str, Path]:
    base = ROOT / "pack" / "overrides"
    return {str(path.relative_to(base)): path for path in base.rglob("*") if path.is_file()}


def write_mrpack(jars: dict[str, Path]):
    files = []
    for mod in MANIFEST["mods"]:
        jar = jars[mod["file"]]
        files.append({
            "path": f"mods/{mod['file']}",
            "hashes": {"sha1": mod["sha1"], "sha512": mod["sha512"]},
            "env": {"client": mod["client"], "server": mod["server"]},
            "downloads": [mod["url"]],
            "fileSize": mod["size"],
        })
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": MANIFEST["versionId"],
        "name": MANIFEST["name"],
        "summary": MANIFEST["summary"],
        "files": files,
        "dependencies": {"minecraft": MANIFEST["minecraft"], "fabric-loader": MANIFEST["loader"]},
    }
    out = DIST / f"Bingo-26.3-{MANIFEST['versionId']}.mrpack"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_STORED) as pack:
        pack.writestr("modrinth.index.json", json.dumps(index, indent=2, ensure_ascii=False))
        side = "overrides/mods/" + OWN_MOD.name
        pack.write(OWN_MOD, side)
        for relative, path in overrides_files().items():
            pack.write(path, f"overrides/{relative}")
    log(f"wrote {out}")


def write_server_zip(jars: dict[str, Path]):
    launcher = download(LAUNCHER_URL, CACHE / LAUNCHER_NAME)
    out = DIST / "bingo-26.3-server.zip"
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as pack:
        for mod in MANIFEST["mods"]:
            if mod["server"] == "unsupported":
                continue
            pack.write(jars[mod["file"]], f"mods/{mod['file']}")
        pack.write(OWN_MOD, f"mods/{OWN_MOD.name}")
        pack.write(launcher, launcher.name)
        pack.writestr("start.sh", START_SH.format(launcher=launcher.name))
        pack.writestr("start.bat", START_BAT.format(launcher=launcher.name))
        pack.writestr("eula.txt", "# Set to true after reading https://aka.ms/MinecraftEULA\neula=false\n")
        pack.writestr("server.properties", SERVER_PROPERTIES)
        for relative, path in overrides_files().items():
            if relative.startswith("config/"):
                pack.write(path, relative)
        pack.writestr("README.md", SERVER_README)
    log(f"wrote {out}")


def write_mods_folder(jars: dict[str, Path]):
    target = DIST / "mods"
    if target.exists():
        shutil.rmtree(target)
    target.mkdir(parents=True)
    for mod in MANIFEST["mods"]:
        if mod["client"] == "unsupported":
            continue
        shutil.copy2(jars[mod["file"]], target / mod["file"])
    shutil.copy2(OWN_MOD, target / OWN_MOD.name)
    log(f"wrote {target}/ ({len(list(target.iterdir()))} jars)")


START_SH = """#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
exec java -Xms2G -Xmx4G -XX:+UseG1GC -jar {launcher} nogui
"""

START_BAT = """@echo off
cd /d %~dp0
java -Xms2G -Xmx4G -XX:+UseG1GC -jar {launcher} nogui
pause
"""

SERVER_PROPERTIES = """# Bingo! 26.3 server
level-name=world
gamemode=adventure
difficulty=normal
allow-flight=true
spawn-protection=0
view-distance=10
simulation-distance=6
max-players=32
online-mode=true
enable-command-block=true
motd=\\u00a7aBingo! \\u00a7f26.3
"""

SERVER_README = """# Bingo! 26.3 server

1. Read https://aka.ms/MinecraftEULA and set `eula=true` in `eula.txt`.
2. Start with `./start.sh` (Linux/macOS) or `start.bat` (Windows).
3. Join and run `/bingo join red`; press Ready when your team is complete.
4. Operators start with `/bingo start` (or `/bingo start force`).

Every round creates a fresh arena dimension with a random seed. Configure
everything in `config/bingo-ourselves/server.json` or with `/bingo` commands.
"""


def main():
    if not OWN_MOD.exists():
        sys.exit(f"Missing {OWN_MOD}. Run ./gradlew build first.")

    jars: dict[str, Path] = {}
    for mod in MANIFEST["mods"]:
        log(f"* {mod['slug']} {mod['version']}")
        jars[mod["file"]] = download(mod["url"], CACHE / mod["file"], mod["sha1"])

    write_mrpack(jars)
    write_server_zip(jars)
    write_mods_folder(jars)

    sums = []
    for artifact in [DIST / f"Bingo-26.3-{MANIFEST['versionId']}.mrpack", DIST / "bingo-26.3-server.zip"]:
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        sums.append(f"{digest}  {artifact.name}  ({artifact.stat().st_size} bytes)")
    (DIST / "SHA256SUMS.txt").write_text("\n".join(sums) + "\n", encoding="utf-8")
    log("wrote dist/SHA256SUMS.txt")
    log("done.")


if __name__ == "__main__":
    main()
