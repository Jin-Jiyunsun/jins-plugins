# Trawling Plus

<p align="center">
<img width="1614" height="1120" alt="splash" src="https://github.com/user-attachments/assets/e925e034-e2e1-4ac8-88a8-b09a4f61b4b2" />
</p>

Shows the routes that deep sea trawling shoals swim, so you can find and follow one without
guessing where it's headed next. Marks stops, fishing range and dangerous water, tracks your nets,
bait and cargo hold from a display on the boat, and keeps a trawling log of everything you catch.

## Features

### Routes

- **Shoal routes**: the nearest shoal's route, drawn in the water as a loop or the next few stops,
  with stops marked and an arrow showing which way the shoal is heading. Also optionally on the minimap and
  world map.
- **Fishable area**: roughly how close your boat needs to be to fish the nearest shoal, with a dot on the
  boat showing whether you're in range. Turns gold while the shoal is shimmering, glistening or
  vibrant.
- **Dangerous water**: route stretches near sea creatures that can attack boats are drawn in red, with
  skulls on the world map.
- **Side panel depth guide**: highlights the depth each net should be at, and ticks it once it is.

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

### Notifications

Optional alerts for full nets, a full hold, a shoal about to leave its stop or changing to the
wrong depth, a shoal turning special, or being knocked off the net.

On someone else's boat, the routes, fishable area, depth guide and notifications still work.

## Trawling log

<!-- trawling log webp -->

A button on the cargo hold screen opens a book of your trawling:

- **Catches**: fish caught this session and in your lifetime, with stars for lifetime milestones.
- **Statistics**: fish per hour, Trawler's trust's extra fish, nets filled, your best session and
  bait used.
- **Worker log**: your and your crew's catches, time on the nets, and your hardest working and top
  catching crewmates.
- **Bookmark**: click the ribbon on a page to set the page the book will open to at the start of each session.

Shift right-click the button or the total info box to reset the lifetime counts.

<p align="center">
  <img width="800" height="669" alt="nextstop" src="https://github.com/user-attachments/assets/0bc124e7-0984-4843-bc2c-130bedfb8747" />
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
- **Fish**: switch each fish off to hide its routes and shoals.

### License

BSD-2-Clause. See [LICENSE](LICENSE).

While tested by myself in game, this plugin was entirely generated using AI.
