"""Generates src/main/resources/com/trawlingplus/routes.json from tools/recorded_routes.json and tools/species.json.

Every route the plugin draws was recorded in-game for Trawling Plus, one lap each, and is kept in
tools/recorded_routes.json. Which bait each kind of shoal takes, whatever its route, is kept by hand in
tools/species.json. Re-run this whenever either changes:

    python tools/generate_routes.py

A route's "safe" field (stretches marked safe by hand despite sitting close to a spawn point) and "threats"
field (where sea creatures that attack boats spawn close enough to threaten it) are not generated: add them to
routes.json by hand, and a later run carries each forward for any route whose name still matches.
"""

import json
import math
import re
from pathlib import Path

OUTPUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "com" / "trawlingplus" / "routes.json"
RECORDED = Path(__file__).resolve().parent / "recorded_routes.json"
SPECIES_DATA = Path(__file__).resolve().parent / "species.json"

# Species a route can be recorded for. The mixed shoals swim the routes of these, so they have none.
SPECIES = ["Giant krill", "Haddock", "Yellowfin", "Halibut", "Bluefin", "Marlin"]

# A recorded stop further than this from its route's path is flagged, in tiles.
STOP_TOLERANCE = 1.0

# Fields hand-added to a route in routes.json rather than generated, carried forward as-is on every run.
HAND_KEPT_ROUTE_FIELDS = ["safe", "threats"]


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
    # Keep each [x, y] pair, or [x, y, tiles] safe circle, on one line so the file stays readable and diffs
    # stay small.
    number = r"-?\d+(?:\.\d+)?"
    text = re.sub(rf"\[\s+({number}),\s+({number}),\s+({number})\s+\]", r"[\1, \2, \3]", text)
    return re.sub(rf"\[\s+({number}),\s+({number})\s+\]", r"[\1, \2]", text)


def main():
    recordings = json.loads(RECORDED.read_text(encoding="utf-8"))["routes"]
    unknown = sorted({r["species"] for r in recordings} - set(SPECIES))
    if unknown:
        raise SystemExit(f"unknown species in {RECORDED.name}: {', '.join(unknown)}")

    known = json.loads(SPECIES_DATA.read_text(encoding="utf-8"))["species"]
    missing = sorted(set(SPECIES) - {entry["name"] for entry in known})
    if missing:
        raise SystemExit(f"no entry in {SPECIES_DATA.name} for: {', '.join(missing)}")

    # Hand-added fields aren't generated, so carry each forward from whatever is there already rather than
    # losing it every time this runs.
    hand_kept_by_route = {}
    if OUTPUT.exists():
        for entry in json.loads(OUTPUT.read_text(encoding="utf-8"))["species"]:
            for route in entry["routes"]:
                kept = {field: route[field] for field in HAND_KEPT_ROUTE_FIELDS if field in route}
                if kept:
                    hand_kept_by_route[route["name"]] = kept

    species = []
    for entry in known:
        name = entry["name"]
        routes = []
        for recording in sorted((r for r in recordings if r["species"] == name), key=lambda r: r["route"]):
            for stop in recording["stops"]:
                off = distance_to_loop(stop, recording["path"])
                if off > STOP_TOLERANCE:
                    print(f"  warning: {name} / {recording['route']}: stop {stop} is {off:.1f} tiles from the path")
            route = {
                "name": recording["route"],
                "recorded": recording["recorded"],
                "world": recording["world"],
                "lapTicks": recording["lapTicks"],
                "stopTicks": recording.get("stopTicks", 0),
                "stops": recording["stops"],
                "path": recording["path"],
            }
            route.update(hand_kept_by_route.get(recording["route"], {}))
            routes.append(route)
        # Every kind of shoal is written out, recorded routes or not, so what is known about it can be used
        # on any shoal of that kind.
        species.append({
            "name": name,
            "bait": entry["bait"],
            "routes": routes,
        })

    data = {
        "about": "Shoal routes recorded in-game for Trawling Plus.",
        "format": "Each kind of shoal has the bait it takes (\"any\" for both kinds of offcuts, \"fine\" for fine fish "
                  "offcuts only) and its recorded routes. Each route is a loop. Stops and path points are [x, y] world tiles on plane 0, "
                  "to a quarter of a tile, listed in the order shoals swim them; the last point connects back to the first. A route may "
                  "also have \"safe\": stretches marked safe by hand as [x, y, tiles] circles, regardless of how close a spawn point "
                  "sits; and \"threats\": where sea creatures that attack boats spawn close enough to threaten it, from the OSRS wiki, "
                  "as its name, combat level and spawn points. Neither is generated; both are kept as-is by tools/generate_routes.py.",
        "species": species,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(dump(data) + "\n", encoding="utf-8")
    print(f"Wrote {sum(len(s['routes']) for s in species)} recorded route(s) to {OUTPUT}")


if __name__ == "__main__":
    main()
