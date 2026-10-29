# Project EVOLVE: architecture

EVOLVE is a world simulation in which civilizations, ecosystems and
environmental rules shape one another, shown in Minecraft through a Fabric mod.

## Platform decision

| Option | Verdict |
|---|---|
| **Fabric mod, Minecraft Java 26.1.2** | **Chosen.** The repository was already a Fabric mod skeleton, Java Edition exposes the APIs we need (server ticks, block access, entity attributes such as `gravity`, commands), and 26.1 is unobfuscated so the mod builds against Mojang's own names with no mapping layer. |
| Bedrock / Education add-on | Rejected: scripting API can't host a persistent multi-system simulation with its own save data at this depth. |
| Custom voxel engine | Rejected for now: rendering, chunking, input and persistence would consume the time budget before any simulation exists. The core below stays engine-independent, so this remains possible. |

The decisive design choice is the split:

```
core/   engine-independent simulation (pure Java 21, no dependencies)
        - deterministic, seeded, fixed daily step
        - unit-tested and runnable headless (CLI demo, benchmarks)
mod/    Fabric adapter for Minecraft 26.1.2 (Java 25)
        - compiles core's sources in; maps cells to chunks
        - turns world actions into block changes, reads the real world back
```

Minecraft is the window into the simulation, never its source of truth for
rules. Nothing in `core/` imports Minecraft, and every decision is made by
explicit rules that tests can check.

## Simulation model

- **Grid.** The world is a grid of cells; in game one cell is one chunk
  (16 x 16 blocks). Cells hold ecological state: biome, standing timber
  (in logs, i.e. log blocks), forage (food units), herbivores, built area and
  saplings.
- **Regions.** Rectangular blocks of cells (8 x 8 by default) that share an
  `EnvironmentProfile`: gravity, fertility, wood decay and storminess, each with
  a base value and hard bounds.
- **Settlements.** Towns and outposts (an outpost has a parent town). Each has
  population, food/timber/stone stocks, a strategy, the last decision with its
  scores, a labour split, and a bounded memory of events that fade with a
  half-life.
- **Buildings, trade routes.** Buildings have type, material, storeys,
  condition and, once placed in game, a block anchor. Routes join two
  settlements.
- **History.** `EventLog` records every significant change with a stable id
  and an optional `causeId`, so causal chains can be walked backwards.
- **World actions.** Changes the host must show (fell trees, plant saplings,
  construct, decay, lay road). The simulation applies each change to its own
  state immediately; actions only describe it to the host, persist until
  acknowledged, and are applied idempotently.

### Daily step

```
day++ -> EnvironmentSystem -> EcosystemSystem -> CivilizationSystem -> settle actions
```

The environment changes first, the ecosystem responds to today's conditions,
then settlements plan on what they observe. Subsystems communicate only
through `WorldState`, `Ecology` (the extraction interface) and events.

### Determinism

- Randomness is stateless and counter-based (`DetRandom`): a draw is a pure
  function of `(seed, day, stream, keys...)`. No generator state is saved, and
  results do not depend on iteration order.
- Collections iterate in a fixed order (lists and arrays, `EnumMap`, never
  hash order). Simultaneous updates (migration) are double-buffered.
- Java `double` arithmetic is deterministic (strict IEEE since Java 17), and
  the save format round-trips every value exactly.
- Test: a run continued in memory and the same run saved, loaded and continued
  produce identical states.

## Rules

Values are defaults from the config classes; units are per cell per day
unless stated.

### Environment (`env/`)

1. **Seasons.** 10 days each; growth multiplier spring 1.25, summer 1.0,
   autumn 0.75, winter 0.25. `growthFactor = fertility x season.growth`.
2. **Gravity flux.** Anomalous regions oscillate:
   `gravity = base + amplitude x sin(2 pi day / period + phase)`, clamped.
3. **Deforestation feedback.** When a region's forest cover falls below half
   its initial cover, its fertility target drops (soil erosion) and its
   storminess target rises (no windbreak), in proportion to the loss. Values
   move toward their targets slowly; when cover recovers, targets return to
   base.
4. **Storms.** Daily chance = storminess. A storm knocks down part of the
   standing timber in some of the region's cells and damages buildings
   (timber worst, stone least, taller buildings more).
5. **Bounds and budget.** Every property is clamped to its bounds after
   every change; the system costs O(regions + buildings in storm regions) per
   day; shift events are rate-limited.

### Ecosystem (`eco/`)

1. **Timber.** Logistic growth toward capacity (biome capacity times unbuilt
   share), scaled by `growthFactor`, plus seed rain from forested neighbours
   so cleared land can recover. Planted saplings mature into logs over about a
   month. No die-back above capacity (the real world may hold more trees than
   the model expects).
2. **Forage.** Logistic regrowth toward biome capacity, scaled by
   `growthFactor`.
3. **Herbivores.** Eat forage; births scale with how well fed they are and
   with habitat quality, which depends on forest cover (animals need cover).
   Starvation and crowding raise deaths. Hungry or exposed animals migrate to
   the best neighbouring cell.
4. **Reclamation.** Ruined buildings free their built area and seed
   saplings, so forest returns to abandoned sites.
5. **Observation.** The host's measured timber replaces the model's value
   (the real world is authoritative where it is loaded).

### Civilization (`civ/`)

1. **Needs.** Food (1 per person per day, reserve target in days), timber and
   stone for planned construction and repairs, housing.
2. **Strategy** (re-evaluated every few days, or at once on a shortage):
   utility scores for EXPAND, CONSERVE, TRADE and OUTPOST from timber
   scarcity, regrowth potential, trade partners' surplus, the best outpost
   site's value net of distance and gravity, population, and memory biases.
   The current strategy gets a small hysteresis bonus. The decision is stored
   with every score and reason.
3. **Labour.** About 60% of people work. Food comes first (fields, gathering,
   hunting by best yield), then construction, logging, quarrying, replanting,
   hauling. Gravity makes logging and especially quarrying and hauling slower.
4. **Construction.** Next building from needs (housing, fields, storage,
   trading post, outpost hall). **Style rules** choose material from local
   resources and the region's rules: timber where forests stand and wood
   decays slowly, stone where quarries are rich (cost scaled by gravity), mud
   brick near wetland or desert. Storeys scale inversely with gravity; storm
   memories keep buildings low.
5. **Trade.** A route to the best partner with surplus; daily exchange with
   hauling losses proportional to distance times gravity; routes close when
   supply dries up.
6. **Outposts.** Founded at the best site within range; they log, ship
   timber home (minus hauling loss) and build in their own region's style.
   Abandoned when no longer needed or no longer viable.
7. **Memory.** Shortages, famines, storm damage, trade outcomes and outpost
   outcomes are remembered with a half-life and bias later decisions (for
   example, a remembered timber shortage raises the appeal of conservation and
   stone).
8. **Abandonment.** A town with no people is abandoned; its buildings decay
   and are reclaimed by forest.

## Minecraft mapping (mod)

| Simulation | In game |
|---|---|
| Cell | Chunk; the sim grid is anchored at a chunk when `/evolve start` runs |
| Standing timber | Log blocks, sampled from loaded chunks and reported back |
| FellTrees / PlantSaplings | Trees cut down / saplings planted in that chunk |
| Building | Procedural structure whose palette comes from material and region, height from storeys |
| Decay | Blocks crumble, moss and vines spread, the ruin finally disappears |
| Trade route | Dirt path between settlements |
| Region gravity | `gravity` (and fall damage) attribute modifiers on entities in the region |
| History, decisions | `/evolve` commands and broadcast messages |

Block work runs from a queue with a per-tick budget, so the server tick is
never stalled.

## Persistence

`persist/` writes the whole `WorldState` as versioned JSON (format version
in every file). Saves are atomic (temp file, then move) and keep the
previous save as a backup; a corrupt or missing file falls back to the backup
with a logged warning. In game the file lives at `<world>/evolve/state.json`.
