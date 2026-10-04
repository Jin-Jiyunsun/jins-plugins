# Trawling Plus

<p align="center">
<img width="1614" height="1120" alt="splash" src="https://github.com/user-attachments/assets/e925e034-e2e1-4ac8-88a8-b09a4f61b4b2" />
</p>

Shows the routes that deep sea trawling shoals swim, so you can find and follow one without
guessing where it's headed next. Marks stops, fishing range and dangerous water along the way,
and keeps track of your nets, bait and cargo hold from a HUD on the boat.

## Features

- **Shoal routes drawn as a loop or as an individual slice**: every route is drawn in game and
  optionally on the world map/minimap, with stops marked and an arrow showing which way the
  nearest shoal is currently heading. The plugin automatically shows you the nearest route.
- **Fishable area**: a square or circle round the nearest shoal showing how close your boat needs
  to be to fish it, plus a dot on the boat showing whether you're in range.
- **Dangerous water**: on the world map, stretches of route that pass close to where sea creatures
  that attack boats spawn are marked with a skull icon, alongside the stretch of route they
  threaten.
- **Heads up display**: a pill on the boat showing time left at the current stop, fish caught in
  the nets, bait remaining, a hold-full warning and each net's depth.
- **Cargo hold display**: while fishing the fishing spots at sea with a rod, net, harpoon, lobster
  pot or karambwan vessel, a second pill shows how many fish are in your boat's hold, out of how
  many fit alongside the rest of your cargo, and how many of each of the last three kinds you put
  in, with any others together. It stacks above the heads up display, works on a raft too, and
  warns when the hold is full. Open the hold once each session so it can count what's inside.
- **Fishing spot arrow**: after fishing one of the spots at sea, an arrow beside your boat points
  the way back to it, such as after banking. It hides while you fish, near the spot, and when your
  hold is nearly full.
- **Side panel depth guide**: highlights which depth each trawling net should be set to, and ticks
  it once it's set correctly.
- **Notifications**: optional alerts for full nets, a full cargo hold, or a shoal about to leave
  its stop.

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

- **Show guides**: whether route lines, arrows and stop markers show all the time or only while
  your boat has trawling nets fitted.
- **Show on maps**: minimap, world map, both or off. The world map is also where dangerous water
  is marked.
- **Clear around boat**: hides the route line, direction arrows and stops under your boat, fading
  them out around its hull.
- Each part of the display is toggleable. The route line, direction arrows, stops, the shoal's
  heading arrow, the fishable area, the heads up display and the side panel have their own
  section in the config panel.
- **Display position**: the heads up display can sit at the helm, the bow or above the sails.
- **Timer bar**: moves the game's stop timer bar from over the shoal to the bottom of the heads up
  display, sized to match it.
- **Animated**: the heads up display fades its lines in and out and grows to fit them. Turn it off
  for instant changes.
- **Cargo hold**: shows or hides the cargo hold display, in the heads up display section.
- **Fishing spot arrow**: shows or hides the fishing spot arrow and sets its colour.
- **Arrow style**: arrows on the water can face the camera (the default), stand upright along the
  route, or lie flat on the water.
- **Fish**: switch each fish on or off to focus on the ones you're after. A fish that's switched
  off has its routes, shoals and map markers hidden.

### License

BSD-2-Clause. See [LICENSE](LICENSE).

While tested by myself in game, this plugin was entirely generated using AI.
