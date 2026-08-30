# Tab Keybind Display

A RuneLite plugin that draws a small text label on top of each interface tab
(Combat, Skills, Inventory, Prayer, etc.) showing the keybind you actually
use to open it — no manual setup required.

## How it works

The plugin reads the same client-side varbits the game itself uses to track
each tab's assigned hotkey, resolves them to their display text via the
game's own key-label data, and cross-checks RuneLite's built-in Key
Remapping plugin so it shows what you actually press if you've remapped
your F-keys. Everything is cached and only recomputed when something
relevant actually changes (you rebind a key, log in/out, hop worlds, or
toggle key remapping) rather than every frame.

It handles all three interface layouts (fixed classic, resizable classic,
resizable modern) automatically, since each one uses a different underlying
widget for the same tab — it checks whichever one is currently visible.

## Customization

Every tab has its own settings section in the plugin's config panel:

- **Color** — per tab, or use one global color for all tabs (toggle at the
  top of the config).
- **Position** — Centre, one of the four corners, or Custom (which uses
  that tab's own X/Y pixel offsets, measured from centre). Can also be set
  globally to override every tab at once.
- **X/Y offset** — only used when Position is Custom on that tab.

A handful of tabs (Combat, Quests, Spellbook, Clan, Music) also get a small,
non-configurable pixel correction in Centre mode, since their icon art isn't
perfectly centered within the tab's actual clickable bounds — this is
intentionally invisible in the config, it's just there so the text lines up
visually without you having to fix it yourself.

Long or awkward key names (Backspace, Page Up/Down, Delete, Insert,
Windows, Numpad keys, punctuation like `;` or `#`) are automatically
shortened or converted to symbols so they fit within a tab's bounds. If you
find one that still displays as a spelled-out word, that's a gap in the
lookup table in `TabKeybindsPlugin#shortKeyName` — worth reporting or
fixing.

## Setup (development)

1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/) (Community is
   fine) and JDK 11.
2. Open this folder as a Gradle project.
3. Run `TabKeybindsPluginTest` (in `src/test/java/com/tabkeybinds`) to
   launch a dev client with this plugin loaded. See RuneLite's
   ["Creating Plugin Hub Plugins"](https://github.com/runelite/runelite/wiki/Creating-Plugin-Hub-Plugins)
   guide for the full external-plugin dev setup if you're starting fresh.
4. Log in via a Jagex account per RuneLite's
   ["Using Jagex Accounts"](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
   instructions.

## Files

- `TabKeybindsPlugin.java` — resolves each tab's varbit to display text
  (including Key Remapping awareness), caches results, registers the
  overlay.
- `TabKeybindsConfig.java` — global settings plus a per-tab config section
  (color, position, X/Y offset) for all 14 tabs.
- `TabKeybindsOverlay.java` — finds the correct widget for each tab in
  whichever layout is active and draws the resolved keybind text at the
  configured position, with a black outline for readability.

## Known limitations

- The Key Remapping integration depends on
  `net.runelite.client.plugins.keyremapping`'s internal `Config`/`Plugin`
  classes directly, since that's a built-in plugin rather than a published
  API. If RuneLite restructures that plugin, this feature could break
  without a compile error.
