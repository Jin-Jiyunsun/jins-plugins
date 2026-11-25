# Trawling Plus

<p align="center">
<img width="1845" height="1089" alt="splash" src="https://github.com/user-attachments/assets/35086c7b-f876-4180-81cb-c026e712ed73" />
</p>

Shows the routes that deep sea trawling shoals swim, so you can find and follow one without
guessing where it's headed next. Marks stops, fishing range and dangerous water, tracks your nets,
bait and cargo hold from a display on the boat, and keeps a trawling log of everything you catch.

## Features

### Routes

- **Shoal routes**: the nearest shoal's route, drawn in the water as a loop or the next few stops,
  with stops marked and an arrow showing which way the shoal is heading. Also optionally on the minimap and
  world map, with arrows and stops along the route and each shoal in sight shown as an arrow while it swims or a dot at a stop.
  Hover a route on the world map for its stops, lap time and time at each stop.
- **Fishable area**: roughly how close your boat needs to be to fish the nearest shoal, with a dot on the
  boat showing whether you're in range. Turns gold while the shoal is shimmering, glistening or
  vibrant.
- **Dangerous water**: route stretches near sea creatures that can attack boats are drawn in red, with
  skulls on the world map.
- **Side panel depth guide**: highlights the depth each net should be at, and ticks it once it is.
- **Predicted shoals**: shoals keep exact time, so the world map keeps showing the last shoal you trawled where it
  likely is while it's out of sight, with its stop timer at a stop. While you're aboard, arrows off your boat and at
  the edge of both maps point back to it, such as after banking, for 10 minutes after your last catch. Right-click a
  shoal to track it without fishing it first, or to untrack it; chat says which shoal you're tracking.

<p align="center">
<img width="800" height="693" alt="prediction" src="https://github.com/user-attachments/assets/2981f557-d8c4-40f6-8068-472232ba949c" />
</p>

### On your boat

- **Heads up display**: time left at the stop, fish in the nets, bait left, net depths and a
  hold-full warning. Hides while you're not trawling.
- **Cargo hold display**: while sea spot fishing (such as anglerfish), how full your hold is and the last few kinds of
  fish you put in. Starts tracking once you view the cargo hold's contents.
- **Fishing spot arrow**: points back to your sea spot after you leave it, such as to bank.
- **Cargo hold highlight**: outlines your hold once your inventory is full while sea spot fishing.
- **Net highlights**: a lowered net with nobody on it is outlined in red, and full nets in yellow.

### Tracking

- **Info boxes**: fish caught this session by kind and in total, with your lifetime count on hover,
  and optionally the fish in your nets.
- **Trawling log**: a book on the cargo hold screen. See below.

### Ocean encounters

Optional outlines and alerts for giant clams that are empty or offering a pearl, clue turtles, and lost crates and
caskets from the tier you pick.

### Notifications

Optional alerts for full nets, a full hold, a shoal leaving, changing depth or turning special, and numb hands, with optional plain messages.

On someone else's boat, everything works except the cargo hold features, the hold full warning and your crew's
stats, with the fish count following your own net there.

## Trawling log

A button on the cargo hold screen opens a book of your trawling. To read it anywhere on the boat, even while
working the nets, right-click the cargo hold and choose Open Trawling Log.

- **Catches**: fish caught this session and in your lifetime, with stars for lifetime milestones.
- **Statistics**: fish per hour, Trawler's trust's extra fish, nets filled, your best session and
  bait used.
- **Worker log**: your and your crew's catches, time on the nets, and your hardest working and top
  catching crewmates.
- **Curiosities**: a page for each rare find from trawling (the six rare fish, Angler's paint and Soup), written up
  when you or your crew find it. Ones found before are added when you open your collection log.
- **Section ribbons**: click a ribbon under the book to jump to that section, or turn them off for a cleaner look.
- **Bookmark**: click the ribbon on a page to set the page the book will open to at the start of each session.

Right-click the button or the total info box to reset the session, or Shift right-click to reset the lifetime counts.

<p align="center">
<img width="800" height="508" alt="trawlinglog" src="https://github.com/user-attachments/assets/7b9ab598-e055-4cb8-a2da-4ec909397d6c" />
</p>

## Route data

Every route was recorded in-game and is accurate to the tile when no smoothing is applied:

- **Giant krill** (any bait): Great Sound, Sunset Bay, The Simian Sea, Turtle Belt
- **Haddock** (any bait): Anglerfish's Light, Misty Sea, Sea of Shells
- **Yellowfin** (any bait): Deepfin Point, Sea of Souls, The Crown Jewel
- **Halibut** (fine bait): Port Roberts, Southern Expanse
- **Bluefin** (fine bait): Buccaneers' Haven, Rainbow Reef
- **Marlin** (fine bait): Brittle Isle, Weissmere

## Settings

Highly customisable: almost every feature can be switched off or recoloured. Here's a quick rundown of some settings:

- **Show guides**: show routes all the time, or only while your boat has trawling nets fitted.
- **Route display**: the whole route, or only the next few stops.
- **Show danger**: mark dangerous water on the route, the maps, both or neither.
- **Display position**: put the heads up display at the helm, bow, sails or over the shoal.
- **Timer bar**: move the game's stop timer bar into the heads up display.
- **Fish**: switch each fish off to hide its routes.

<p align="center">
<img width="800" height="605" alt="nextstop" src="https://github.com/user-attachments/assets/21160c9e-b8d0-4d92-a2e7-e460ecef4e99" />
</p>
<p align="center">
<img width="1400" height="700" alt="fishableareadanger" src="https://github.com/user-attachments/assets/2af32105-cace-4c28-9330-a75779789e94" />
</p>

## Disclaimer on AI & License

This plugin is developed with heavy AI assistance (Claude). Everything is tested in game by myself before shipping.

BSD-2-Clause. See [LICENSE](LICENSE).
