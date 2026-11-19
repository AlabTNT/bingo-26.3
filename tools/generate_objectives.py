#!/usr/bin/env python3
"""Build the engine's objective pools from the reference tier lists + our mission tree.

Inputs:
  --item-tierlist   reference item tier list (S/A/B/C/D JSON)
  --adv-tierlist    tools/out/advancement_tiers.json (default)
  --advancements    our generated advancement directory

Outputs:
  src/main/resources/data/bingo_ourselves/bingo/items.json
  src/main/resources/data/bingo_ourselves/bingo/advancements.json
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ORDER = ["S", "A", "B", "C", "D"]
TIER = {name: index for index, name in enumerate(ORDER)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--item-tierlist", type=Path, required=True)
    ap.add_argument("--adv-tierlist", type=Path, default=ROOT / "tools/out/advancement_tiers.json")
    ap.add_argument("--advancements", type=Path, required=True)
    args = ap.parse_args()

    items_tier = json.loads(args.item_tierlist.read_text(encoding="utf-8"))
    items = []
    seen = set()
    for tier in ORDER:
        for entry in items_tier.get(tier, []):
            if not entry.startswith("minecraft:") or entry in seen:
                continue
            seen.add(entry)
            items.append({"id": entry, "tier": TIER[tier]})

    adv_tier = json.loads(args.adv_tierlist.read_text(encoding="utf-8"))
    advancements = []
    for tier in ORDER:
        for entry in adv_tier.get(tier, []):
            rel = entry.removeprefix("bingo_ourselves:")
            file = args.advancements / f"{rel}.json"
            if not file.exists():
                continue
            data = json.loads(file.read_text(encoding="utf-8"))
            icon = data.get("display", {}).get("icon", {}).get("id", "minecraft:paper")
            advancements.append({"id": entry, "tier": TIER[tier], "icon": icon})

    out = ROOT / "src/main/resources/data/bingo_ourselves/bingo"
    out.mkdir(parents=True, exist_ok=True)
    (out / "items.json").write_text(
        json.dumps({"items": items}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    (out / "advancements.json").write_text(
        json.dumps({"advancements": advancements}, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(f"items: {len(items)}, advancements: {len(advancements)}")


if __name__ == "__main__":
    main()
