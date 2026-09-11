"""Generates src/main/resources/com/trawlingplus/routes.json from the OSRS Wiki.

Shoal route data on the Old School RuneScape Wiki is licensed under CC BY-NC-SA 3.0, so
the generated file carries that license and an attribution line; the plugin's own code
stays BSD-2. Re-run this whenever the wiki's shoal routes change:

    python tools/generate_routes.py
"""

import json
import math
import re
import time
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "trawling-plus route generator (https://github.com/Jin-Jiyunsun/jins-plugins)"
OUTPUT = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "com" / "trawlingplus" / "routes.json"

SPECIES = [
    ("Giant krill", "Giant_krill_shoal"),
    ("Haddock", "Haddock_shoal"),
    ("Yellowfin", "Yellowfin_shoal"),
    ("Halibut", "Halibut_shoal"),
    ("Bluefin", "Bluefin_shoal"),
    ("Marlin", "Marlin_shoal"),
]

# How far a stop may sit from its route's path before it's flagged, in tiles.
STOP_TOLERANCE = 5.0


def fetch(page):
    query = urllib.parse.urlencode({
        "action": "parse",
        "page": page,
        "prop": "wikitext|revid",
        "format": "json",
        "formatversion": "2",
    })
    request = urllib.request.Request(API + "?" + query, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        data = json.load(response)
    if "parse" not in data:
        raise SystemExit(f"{page}: {data.get('error', {}).get('info', 'fetch failed')}")
    return data["parse"]["wikitext"], data["parse"]["revid"]


def colon_pairs(text):
    # "x:y,x:y,..." as used by the wiki's line paths, and by one stop list.
    return [[int(v) for v in point.split(":")] for point in text.split(",") if point]


def open_loop(points):
    # The wiki repeats the first point at the end to close each loop; the plugin closes it itself.
    if len(points) > 1 and points[0] == points[-1]:
        return points[:-1]
    return points


def parse_map(template):
    stops, path = [], []
    for param in (p.strip() for p in template.split("|")):
        if param.startswith("mtype:line,"):
            path = colon_pairs(param[len("mtype:line,"):])
        elif re.fullmatch(r"\d+,\d+", param):
            stops.append([int(v) for v in param.split(",")])
        elif re.fullmatch(r"\d+:\d+(,\d+:\d+)*", param):
            stops.extend(colon_pairs(param))
    return open_loop(stops), open_loop(path)


def distance_to_segment(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    t = 0 if length == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def distance_to_loop(point, path):
    return min(distance_to_segment(point, a, path[(i + 1) % len(path)]) for i, a in enumerate(path))


def parse_species(name, page):
    wikitext, revision = fetch(page)

    stop = re.search(r"always stop for \{\{ticks\|(\d+)\}\}", wikitext)
    if not stop:
        raise SystemExit(f"{page}: no stop duration found")

    section = re.search(r"==\s*Routes\s*==(.*?)(?=\n==[^=]|\Z)", wikitext, re.S)
    if not section:
        raise SystemExit(f"{page}: no Routes section found")

    routes = []
    for block in re.split(r"\n===", section.group(1)):
        if not block.strip():
            continue

        route_name = block.split("===", 1)[0].strip("= \n")
        lap = re.search(r"takes ([\d,]+) ticks", block)
        template = re.search(r"\{\{Map\|(.*?)\}\}", block, re.S)
        if not lap or not template:
            raise SystemExit(f"{page} / {route_name}: missing lap time or map")

        stops, path = parse_map(template.group(1))
        if len(stops) < 2 or len(path) < 2:
            raise SystemExit(f"{page} / {route_name}: too few points ({len(stops)} stops, {len(path)} path)")

        for s in stops:
            off = distance_to_loop(s, path)
            if off > STOP_TOLERANCE:
                print(f"  warning: {name} / {route_name}: stop {s} is {off:.1f} tiles from the path")

        lap_ticks = int(lap.group(1).replace(",", ""))
        routes.append({"name": route_name, "lapTicks": lap_ticks, "stops": stops, "path": path})
        print(f"  {route_name}: {len(stops)} stops, {len(path)} path points, lap {lap_ticks} ticks")

    return {
        "name": name,
        "page": f"https://oldschool.runescape.wiki/w/{page}?oldid={revision}",
        "stopTicks": int(stop.group(1)),
        "routes": routes,
    }


def main():
    species = []
    for index, (name, page) in enumerate(SPECIES):
        if index:
            time.sleep(2)  # be polite to the wiki's API
        print(name)
        species.append(parse_species(name, page))

    data = {
        "attribution": "Shoal route data from the Old School RuneScape Wiki (https://oldschool.runescape.wiki), "
                       "licensed under CC BY-NC-SA 3.0 (https://creativecommons.org/licenses/by-nc-sa/3.0/).",
        "format": "Each route is a loop. Stops and path points are [x, y] world tiles on plane 0, listed in "
                  "order around the loop; the last point connects back to the first.",
        "species": species,
    }

    text = json.dumps(data, indent="\t", ensure_ascii=False)
    # Keep each [x, y] pair on one line so the file stays readable and diffs stay small.
    text = re.sub(r"\[\s+(\d+),\s+(\d+)\s+\]", r"[\1, \2]", text)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(text + "\n", encoding="utf-8")
    print(f"Wrote {sum(len(s['routes']) for s in species)} routes to {OUTPUT}")


if __name__ == "__main__":
    main()
