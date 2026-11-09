# UI Spec — Raid Dungeon Menu

Build target for the `mondungeons` raid menu (Stage 3 in DUNGEON_AGENTS.md). This is a **client-side `Screen`** opened when a player uses a dungeon key. A visual mockup exists as a reference artifact; this file is the source of truth for layout, text, colors, and data bindings.

Everything here is a Minecraft GUI drawn with `GuiGraphics` (filled rects for panels, `font.draw` for text, textured/`blitSprite` for item icons) — not HTML. The HTML mockup only shows intended look and arrangement.

## When it opens
- Player right-clicks / uses a dungeon key item → open this screen.
- It reads the **used key's tier** and shows the odds for *that tier only*. A different key tier shows different numbers in the same layout.
- All odds come from `dungeon-loot.json` → `keyTiers[<tier>].tierOdds`. One source of truth: these are the same numbers the end-of-run spin rolls.

## Layout (top to bottom, centered panel)
Panel is a centered rounded dark box, ~460px wide equivalent, over a dimmed/blurred background.

1. **Title bar**
   - Left: title `RAID DUNGEON`, with a smaller subtitle under it: `your odds this run`
   - Right: a close button `X`
2. **Key row** (the key being used)
   - A colored key icon (tinted to the key's tier color)
   - Small tier label (e.g. `RARE KEY`), the key's display name, and a one-line note (`better odds than common — one use`)
3. **Odds header row**: left label `REWARD TIER`, right label `CHANCE`
4. **Four tier rows**, in this order: Jackpot, Rare, Uncommon, Common. Each row:
   - Tier name (left), colored in the tier's color
   - Percent (right), from `tierOdds`
   - A line of example item names from that tier (muted)
   - A horizontal odds bar filled proportional to the percent
5. **Start button**: full-width green `START RUN`
6. **Footer line**: `10 rounds · one trainer at a time · you return here when it's over`

## Text strings (exact)
- Title: `RAID DUNGEON`
- Subtitle: `your odds this run`
- Close: `X`
- Key note: `better odds than common — one use` (vary per tier if you want; optional)
- Odds header: `REWARD TIER` / `CHANCE`
- Tier names: `Jackpot`, `Rare`, `Uncommon`, `Common`
- Start button: `START RUN`
- Footer: `10 rounds · one trainer at a time · you return here when it's over`

## Colors (hex)
| Role | Hex |
| --- | --- |
| Panel background | `#15121E` (≈90% opacity over the dimmed world) |
| Panel inner / slot | `#1D1829` / `#241D33` |
| Border | `#393154` |
| Border highlight | `#4A3F6B` |
| Text primary | `#F1ECFA` |
| Text muted | `#A79FC0` |
| Text faint | `#6F6788` |
| Accent / gold | `#F4C25A` |
| Start button | `#5FCF72` (bottom edge `#2F7D3E`, text `#0B2A14`) |
| Tier — Jackpot | `#F4C25A` |
| Tier — Rare | `#A874E8` |
| Tier — Uncommon | `#4F9DE8` |
| Tier — Common | `#8A93A8` |

## Data bindings
- **Key tier, name, icon color** ← the key item the player used.
- **The four percentages** ← `dungeon-loot.json` → `keyTiers[<keyTier>].tierOdds` (`jackpot`, `rare`, `uncommon`, `common`).
- **Odds bar fill** ← proportional to the percent. DECISION: a true-scale 3% bar is nearly invisible, so either (a) give small values a readable minimum width, or (b) draw literal. The mockup eases them; pick one and keep it consistent.
- **Example item names per tier** ← a few labels pulled from `rewardTiers[<tier>]` in `dungeon-loot.json`. Optional in-game: you may show item icons instead of names, or drop them entirely if space is tight.

## Interactions
- **START RUN** → acquire the global dungeon lock, capture the player's return position, teleport into the arena, begin round 1 (this hands off to Stage 4/5). Consume nothing yet — the key is only consumed on reward (see AGENTS).
- **X / Escape** → close the screen, do nothing else. No lock taken.
- **Locked state**: if the global lock is already held (someone else is mid-run), the Start button is **disabled** and shows `DUNGEON IN USE` instead of `START RUN`. The player can still view odds, just can't start.

## Other screens this feature needs (not this file)
These are separate screens/overlays to spec later — flag if you want them:
- **Spin / payout** — the wheel after round 10 that reveals the reward.
- **Mid-run round counter** — a small HUD showing `Round X / 10` during the gauntlet.
- **Result toast** — what dropped, shown on return.

## Reference
- Odds + reward contents: `dungeon-loot.json`
- Feature flow and stages: `DUNGEON_AGENTS.md` (this is Stage 3)
