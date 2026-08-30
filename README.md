# Tab Keybind Display

A RuneLite plugin that draws a small text label on top of each interface tab
(Combat, Skills, Inventory, Prayer, etc.) showing the keybind you use to open it.

## Why you have to enter the keybinds yourself

RuneLite plugins can't read the game's actual hotkey-to-tab mapping — that's
controlled by OSRS's own client settings (Settings > Controls), not by any
varp/varbit RuneLite's API exposes. So this plugin doesn't try to guess; you
tell it what you've bound each tab to (via the plugin's config panel, using
RuneLite's normal "click to set a keybind" control), and it just labels the
tab with that text. Leave a tab's keybind unset and no label is drawn for it.

It handles all three interface layouts (fixed classic, resizable classic,
resizable modern) automatically, since each one uses a different underlying
widget for the same tab — it checks the visible one at render time.

## Setup

1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community is fine)
   and JDK 11.
2. Open this folder as a Gradle project.
3. Copy it (or symlink it) into a RuneLite `plugin-hub`-style external plugin
   setup, or follow RuneLite's
   ["Creating Plugin Hub Plugins"](https://github.com/runelite/runelite/wiki/Creating-Plugin-Hub-Plugins)
   guide to test it inside a full RuneLite dev environment.
4. Run/debug the client with this plugin enabled, then open its config panel
   and set the keybind for each tab to match what you've bound in-game.

## Files

- `TabKeybindsPlugin.java` — plugin entrypoint, registers the overlay.
- `TabKeybindsConfig.java` — one `Keybind` config option per tab, plus a text
  color option.
- `TabKeybindsOverlay.java` — finds the correct widget for each tab in
  whichever layout is active and draws the configured keybind text centered
  on it.

## Customizing

- Want icons instead of text, or a fixed corner badge instead of centering
  on the tab? Change the drawing logic in `TabKeybindsOverlay#render`.
- Want it to only show labels while a modifier key is held (like many "hold
  Alt to see keybinds" UIs)? Track key state with RuneLite's `KeyManager`
  in the plugin class and check it before drawing in the overlay.
