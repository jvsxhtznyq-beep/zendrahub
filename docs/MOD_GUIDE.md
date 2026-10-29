# Project EVOLVE: mod guide

EVOLVE runs a living simulation inside a Minecraft world. Settlements plan,
build, log and trade. Forests and wildlife respond. Each region of the world
has its own environmental rules (gravity, fertility, wood decay, storms) that
shape both. What you see in the world (felled forests, new houses, roads,
outposts, ruins being reclaimed by forest) is the simulation's history made
visible.

## Install

Requires **Minecraft Java 26.1.2** and **Java 25**.

1. Install **Fabric Loader** for Minecraft 26.1.2 with the installer from
   [fabricmc.net](https://fabricmc.net/use/). The mod needs Loader 0.18 or
   newer; it is built against 0.20.1.
2. Download **Fabric API** for 26.1.2 and put it in your `mods` folder
   (`.minecraft/mods`, or the server's `mods/`).
3. Put `evolve-<version>.jar` in the same folder. Either take it from the
   repository's CI build artifacts, or build it with `./gradlew :mod:build`
   (the jar appears in `mod/build/libs/`).

The mod runs on the server side, including the built-in server in
singleplayer. On a dedicated server, players do not need the mod installed.

## Quick start

1. Open a world and stand where your town should be: dry land, ideally near
   some forest.
2. Run `/evolve start Oakridge` (any name).
   - A **32 x 32 chunk area** (512 x 512 blocks) centred on you becomes the
     simulated world, split into **16 regions** of 8 x 8 chunks. Each region
     has its own rules.
   - Each chunk's terrain comes from its biome; forests near you are measured
     block by block.
   - Your town is founded where you stand. A **neighbouring town** is founded
     8 to 14 chunks away, as a trading partner or rival for resources.
3. One simulated day passes every 30 seconds. Try `/evolve speed 100` (five
   seconds a day) or `/evolve advance 30` to see things move.
4. Use `/evolve here`, `/evolve settlements` and `/evolve settlement Oakridge`
   to see what is going on and why.

## Commands

| Command | What it does |
|---|---|
| `/evolve` | List the commands |
| `/evolve start <name>` | Create the simulated world around you and found your town |
| `/evolve found <name>` | Found another town where you stand |
| `/evolve status` | Day, season, population, buildings, routes, queued world changes |
| `/evolve here` | Your chunk's ecology (timber, forage, herbivores) and your region's rules |
| `/evolve settlements` | Every settlement with its population, strategy and stocks |
| `/evolve settlement <name>` | Stocks, housing, strategy, the latest decision with every option's score and the reasons behind it, labour split, memories, buildings, trade routes |
| `/evolve history [count]` | The latest events (default 10, up to 50), with ids |
| `/evolve why <event id>` | The chain of causes behind an event, oldest first |
| `/evolve advance <days>` | Simulate up to 365 days at once |
| `/evolve speed <ticks>` | Server ticks per simulated day (20 to 72000; 20 ticks = 1 second) |
| `/evolve pause` / `/evolve resume` | Stop or restart the clock |
| `/evolve save` | Save now (the mod also saves every five minutes and when the server stops) |

Major events are announced in chat as they happen: strategy changes,
outposts, trade routes, shortages, starvation, storms, environment shifts,
forests lost and regrown.

## What you will see

**Settlements build.** New structures rise near each town's centre: houses,
storehouses with chests, irrigated farms, lumber camps, trading posts with a
bell, and outpost halls with a banner. Their look follows the simulation's
style rules:
- **Material.**
  - **Timber**: the local wood, or dark oak in rot-prone regions.
  - **Stone**: cobblestone, or stone bricks for taller buildings.
  - **Mud brick**: mud bricks on a timber frame, near wetland or desert.
- **Height.** Buildings are taller where gravity is light. The tallest get a
  spire. Where gravity is heavy, or after storm damage, they stay low.

**Forests are used and regrow.** When a town logs a chunk, whole trees
disappear there. A town that switches to conservation plants saplings of the
local species.

**Roads and outposts.** A trade route is laid out as a dirt path between the
two towns. A town that runs out of timber may found an outpost where forests
still stand. Outposts build in their own region's style, so you will see a
stone town's outpost built of timber, or the reverse.

**Ruins return to nature.** Damaged and abandoned buildings weather: fittings
vanish, stone gets moss and cracks, wood rots away. A ruin finally disappears,
its floor turns back to grass, and saplings take root.

**Your actions count.** Chunks near players are surveyed regularly. Trees
you cut or plant change what the settlements see and plan around.

**Gravity is physical.** While you stand in a region, its gravity applies to
you: you jump higher and fall slower where it is light. Fall damage scales
with gravity. Some regions are unstable, and their gravity drifts over time.
`/evolve here` shows the current value.

## Files

In the world folder, under `evolve/`:
- `state.json` holds the whole simulation: versioned and written atomically.
  `state.json.bak` is the previous save. A damaged save is moved aside (as
  `*.corrupt-<time>`) instead of being overwritten.
- `runtime.properties` holds `ticksPerDay`, `blockBudget` (block changes per
  tick, default 200) and `paused`.

## Limitations (milestone 1)

- One simulated area per world, in the overworld only.
- World changes happen only in loaded chunks. Work far from players waits
  until someone comes near, and the queue keeps it in order.
- Settlement populations are simulated, not shown as villagers. Wildlife is
  simulated per chunk and does not spawn or remove vanilla animals.
- The real world corrects the simulation's forest estimates only near
  players. Elsewhere the model's regrowth estimate stands.
- Region gravity applies to players, not to mobs.
- Any player can start the simulation, found towns and inspect everything.
  Changing the clock (`advance`, `speed`, `pause`, `resume`) and `save` need
  game-master permission (an operator, or cheats enabled in singleplayer).
- Repairs are not shown: a repaired building keeps its weathered look.
- The Minecraft calls were written for 26.1.2 and checked against the
  26.1.2 sources of Fabric API, NeoForge and Carpet; the build itself runs in
  GitHub Actions. `mod/API_NOTES.md` lists every assumption.
