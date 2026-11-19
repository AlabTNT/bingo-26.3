#!/usr/bin/env python3
"""Generate the Bingo! 26.3 mission tree from the vanilla 26.3 advancement data.

The pack replaces the five visible vanilla advancement tabs with our own
advancement tree (same progression, own namespace, bilingual titles), used by
the built-in Bingo engine as mission objectives.

Inputs (from a Minecraft 26.3 install / server jar):
  --vanilla-data   extracted server jar data/ directory (data/minecraft/advancement/...)
  --en             assets/minecraft/lang/en_us.json
  --zh             assets/minecraft/lang/zh_cn.json
  --reference-tierlist  optional JSON with S/A/B/C/D mission ids (reference only)

Usage:
  python3 tools/generate_advancements.py \
      --vanilla-data /tmp/mc263data/data \
      --en /tmp/mc263data/assets/minecraft/lang/en_us.json \
      --zh /tmp/mc263data/assets/minecraft/lang/zh_cn.json
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/resources"
MOD_ID = "bingo_ourselves"
CATEGORIES = ["story", "nether", "end", "adventure", "husbandry"]
TAB_NAMES = {
    "story": ("Story", "故事"),
    "nether": ("Nether", "下界"),
    "end": ("The End", "末地"),
    "adventure": ("Adventure", "冒险"),
    "husbandry": ("Husbandry", "农牧业"),
}
HARD = {
    "adventure/adventuring_time",
    "adventure/kill_all_mobs",
    "nether/all_effects",
    "nether/all_potions",
    "husbandry/balanced_diet",
    "husbandry/bred_all_animals",
    "husbandry/complete_catalogue",
    "husbandry/whole_pack",
    "adventure/trim_with_all_exclusive_armor_patterns",
}


def load_lang(path: Path) -> dict:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def text_value(component, lang, fallback=""):
    if isinstance(component, str):
        return component
    if isinstance(component, dict):
        key = component.get("translate")
        if key:
            return lang.get(key, component.get("fallback", key))
        return component.get("text", fallback)
    return fallback


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vanilla-data", type=Path, required=True)
    ap.add_argument("--en", type=Path, required=True)
    ap.add_argument("--zh", type=Path, required=True)
    ap.add_argument("--reference-tierlist", type=Path, default=None)
    args = ap.parse_args()

    en = load_lang(args.en)
    zh = load_lang(args.zh)

    vanilla_tier = {}
    if args.reference_tierlist is not None:
        tier_source = json.loads(args.reference_tierlist.read_text(encoding="utf-8"))
        for tier in ["S", "A", "B", "C", "D"]:
            for entry in tier_source.get(tier, []):
                vanilla_tier[entry.removeprefix("minecraft:")] = tier

    tasks = {}
    roots = {}
    for cat in CATEGORIES:
        cat_dir = args.vanilla_data / "minecraft/advancement" / cat
        for file in sorted(cat_dir.glob("*.json")):
            data = json.loads(file.read_text(encoding="utf-8"))
            name = file.stem
            if name == "root":
                roots[cat] = data
            elif data.get("display"):
                tasks[f"{cat}/{name}"] = data

    # Difficulty tier for our tree: plenty of D/C entries so an "easy" 5x5 card
    # (16 D + 9 C) can always be filled from the mission pool.
    remap = {"D": "D", "C": "D", "B": "C", "A": "B", "S": "A"}
    tiers = {"S": [], "A": [], "B": [], "C": [], "D": []}
    for tid in tasks:
        if tid in HARD:
            tiers["S"].append(tid)
        else:
            if tid in vanilla_tier:
                tiers[remap.get(vanilla_tier[tid], "C")].append(tid)
            else:
                frame = tasks[tid].get("display", {}).get("frame", "task")
                tiers[{"challenge": "B", "goal": "C", "task": "D"}.get(frame, "C")].append(tid)

    out_dir = RES / "data" / MOD_ID / "advancement"
    lang_en = load_lang(RES / "assets" / MOD_ID / "lang" / "en_us.json")
    lang_zh = load_lang(RES / "assets" / MOD_ID / "lang" / "zh_cn.json")

    # Category roots (skipped by the engine because their id ends in /root).
    for cat in CATEGORIES:
        root = roots[cat]
        root_display = root.get("display", {})
        en_tab, zh_tab = TAB_NAMES[cat]
        title_key = f"bo.adv.{cat}.root.title"
        desc_key = f"bo.adv.{cat}.root.description"
        lang_en[title_key] = f"Bingo: {en_tab}"
        lang_zh[title_key] = f"Bingo：{zh_tab}"
        lang_en[desc_key] = f"Complete the {en_tab.lower()} missions to score Bingo tiles."
        lang_zh[desc_key] = f"完成{zh_tab}任务，为 Bingo 棋盘得分。"
        data = {
            "display": {
                "icon": root_display.get("icon", {"id": "minecraft:grass_block"}),
                "title": {"translate": title_key, "fallback": lang_en[title_key]},
                "description": {"translate": desc_key, "fallback": lang_en[desc_key]},
                "background": root_display.get(
                    "background", "minecraft:gui/advancements/backgrounds/stone"
                ),
                "frame": "task",
                "show_toast": False,
                "announce_to_chat": False,
                "hidden": False,
            },
            "criteria": {"impossible": {"trigger": "minecraft:impossible"}},
        }
        target = out_dir / cat / "root.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    for tid, data in tasks.items():
        cat, name = tid.split("/", 1)
        display = data["display"]
        vanilla_title = text_value(display.get("title"), en)
        vanilla_desc = text_value(display.get("description"), en)
        title_key = f"bo.adv.{cat}.{name}.title"
        desc_key = f"bo.adv.{cat}.{name}.description"
        lang_en[title_key] = vanilla_title
        lang_zh[title_key] = text_value(display.get("title"), zh, vanilla_title)
        lang_en[desc_key] = vanilla_desc
        lang_zh[desc_key] = text_value(display.get("description"), zh, vanilla_desc)

        parent = data.get("parent")
        if parent:
            parent_path = parent.removeprefix("minecraft:")
            if not parent_path.endswith("/root") and parent_path not in tasks:
                parent_path = f"{cat}/root"
        else:
            parent_path = f"{cat}/root"

        new = {
            "parent": f"{MOD_ID}:{parent_path}",
            "display": {
                "icon": display.get("icon", {"id": "minecraft:stone"}),
                "title": {"translate": title_key, "fallback": lang_en[title_key]},
                "description": {"translate": desc_key, "fallback": lang_en[desc_key]},
                "frame": display.get("frame", "task"),
                "show_toast": True,
                "announce_to_chat": False,
                "hidden": False,
            },
            "criteria": data["criteria"],
        }
        if "requirements" in data:
            new["requirements"] = data["requirements"]
        target = out_dir / cat / f"{name}.json"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(new, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    # Our own mission tier list (consumed by tools/generate_objectives.py).
    tierlist = {
        "replace": True,
        "values": [],
        **{tier: [f"{MOD_ID}:{tid}" for tid in sorted(tiers[tier])] for tier in ["S", "A", "B", "C", "D"]},
    }
    out_dir = ROOT / "tools" / "out"
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "advancement_tiers.json").write_text(
        json.dumps(tierlist, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    lang_dir = RES / "assets" / MOD_ID / "lang"
    lang_dir.mkdir(parents=True, exist_ok=True)
    for filename, lang in [("en_us.json", lang_en), ("zh_cn.json", lang_zh)]:
        (lang_dir / filename).write_text(
            json.dumps(dict(sorted(lang.items())), indent=2, ensure_ascii=False) + "\n",
            encoding="utf-8",
        )

    print(f"Generated {len(tasks)} missions across {len(CATEGORIES)} tabs.")
    print("Tier counts:", {tier: len(entries) for tier, entries in tiers.items()})


if __name__ == "__main__":
    main()
