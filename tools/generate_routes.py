"""Generates src/main/resources/com/trawlingplus/routes.json from tools/recorded_routes.json.

Every route the plugin draws was recorded in-game for Trawling Plus; tools/import_recording.py adds
recordings to tools/recorded_routes.json. Re-run this whenever a recording is added or replaced:

    python tools/generate_routes.py
"""

import json
import math
import re
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "com" / "trawlingplus" / "routes.json"
RECORDED = Path(__file__).resolve().parent / "recorded_routes.json"

# Species in the order they're unlocked, so routes.json reads in the same order as the game.
SPECIES = ["Giant krill", "Haddock", "Yellowfin", "Halibut", "Bluefin", "Marlin"]

# A recorded stop further than this from its route's path is flagged, in tiles.
STOP_TOLERANCE = 1.0


def distance_to_segment(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    t = 0 if length == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def distance_to_loop(point, path):
    return min(distance_to_segment(point, a, path[(i + 1) % len(path)]) for i, a in enumerate(path))


def dump(data):
    text = json.dumps(data, indent="\t", ensure_ascii=False)
    # Keep each [x, y] pair on one line so the file stays readable and diffs stay small.
    return re.sub(r"\[\s+(-?\d+(?:\.\d+)?),\s+(-?\d+(?:\.\d+)?)\s+\]", r"[\1, \2]", text)


def main():
    recordings = json.loads(RECORDED.read_text(encoding="utf-8"))["routes"]
    unknown = sorted({r["species"] for r in recordings} - set(SPECIES))
    if unknown:
        raise SystemExit(f"unknown species in {RECORDED.name}: {', '.join(unknown)}")

    species = []
    for name in SPECIES:
        routes = []
        for recording in sorted((r for r in recordings if r["species"] == name), key=lambda r: r["route"]):
            for stop in recording["stops"]:
                off = distance_to_loop(stop, recording["path"])
                if off > STOP_TOLERANCE:
                    print(f"  warning: {name} / {recording['route']}: stop {stop} is {off:.1f} tiles from the path")
            routes.append({
                "name": recording["route"],
                "recorded": recording["recorded"],
                "world": recording["world"],
                "lapTicks": recording["lapTicks"],
                "stopTicks": recording.get("stopTicks", 0),
                "stops": recording["stops"],
                "path": recording["path"],
            })
        if routes:
            species.append({"name": name, "routes": routes})

    data = {
        "about": "Shoal routes recorded in-game for Trawling Plus.",
        "format": "Each route is a loop. Stops and path points are [x, y] world tiles on plane 0, to a quarter "
                  "of a tile, listed in the order shoals swim them; the last point connects back to the first.",
        "species": species,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(dump(data) + "\n", encoding="utf-8")
    print(f"Wrote {sum(len(s['routes']) for s in species)} recorded route(s) to {OUTPUT}")


if __name__ == "__main__":
    main()
