# Flashback Inspector (1.21.11 port)

Puts into a [Flashback](https://modrinth.com/mod/flashback) replay what was never there: your
inventory and the contents of every container you opened. During playback the inventory key opens
them as a normal screen, with real tooltips, enchantments, lore and stats, because the file holds
the actual items.

**Minecraft 1.21.11, Fabric. Requires Flashback 0.39.10 or newer for 1.21.11.**

## Credits

This is a port of **[Flashback Inspector by Glam_Ardor](https://codeberg.org/Glam_Ardor/flashback-inspector)**,
which supports Minecraft 1.21.6 to 1.21.8. The idea, the recording format and the original code are
theirs. All credit for the mod goes to them; this repository only adapts it to 1.21.11. It is
licensed under the same terms as the original (LGPL-3.0-or-later, see `LICENSE`).

It also builds on Flashback by Moulberry, which this repository does not include or redistribute.

## Changes from the original

- Updated to Minecraft 1.21.11 (Fabric API, mappings and the changed screen and key binding APIs).
- The inspector now uses the vanilla inventory and container textures and slot positions.
- Opening your own inventory is recorded, so the follow mode reacts to it too.
- The 2x2 crafting grid and its result slot in your own inventory are recorded and shown.
- The container type is recorded (a new small payload), so containers are drawn with their own
  layout: crafting table, furnace, blast furnace, smoker, hopper, dispenser, dropper, brewing
  stand, enchanting table, anvil, smithing table, grindstone and stonecutter. Everything else
  keeps the generic grid. Recordings made without the type information use the generic grid.

## How it works

Flashback records the packets a client receives, and container packets are on its ignore list, so
a normal replay has no inventory. This mod writes its own custom payloads into the same recording
(changed slots every few ticks, plus a full state inside every Flashback snapshot so that seeking
works). During playback it rebuilds the state for the current tick and swaps the inventory screen
for its own. A replay recorded with the mod opens fine without it: unknown payloads are skipped.

## What gets recorded

| | Recorded |
|---|---|
| Your own inventory, armor, both hands | yes, with all item components |
| Containers you opened yourself | yes, contents and every slot change |
| Other players' armor and hands | yes, Flashback already records this |
| Other players' inventories, containers opened by others | no, the server never sends them |

## Controls

- **Inventory key (E)** during playback: the inspector for the player you are following.
- **K**: container log. Clicking an entry seeks the replay to that tick.
- The container mode can be set in the config: manual (default) or follow the recording.

## Building

You need JDK 21.
