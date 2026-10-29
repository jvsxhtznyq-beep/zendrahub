# Progress log

## 2026-10-29: milestone 1

### Status

Milestone 1 (see `MILESTONE-1.md`) is built and verified headless. The
Fabric mod builds against Minecraft 26.1.2 in CI, but **has not yet been
run inside the game**. The build environment cannot reach Mojang's servers,
so playing it is the first in-game test.

| Criterion | Status | Evidence |
|---|---|---|
| 1. Procedurally generated region | done | `WorldGenTest` (9 tests): determinism, variety, 7 region archetypes per 32 x 32 world |
| 2. Settlement with population and resources | done | civilization tests |
| 3. Timber and food economy | done | civilization tests; demo |
| 4. Small ecosystem | done | `EcosystemSystemTest` (12): regrowth, seed rain, saplings, wildlife habitat, migration, no false events over 400 days |
| 5. Environmental rule that changes behaviour | done | gravity, fertility, wood decay and storms; `EnvironmentSystemTest` (10); `IntegrationTest.gravityShapesConstruction`; demo gravity check |
| 6. Decision that responds to conditions | done | `PlannerTest`; `IntegrationTest.depletedForestsPushTownsOffTheExpansionPath` (10 seeds) |
| 7. Visible / inspectable state | done headless; in game untested | `/evolve` commands, `evolve inspect` / `why`, replay viewer |
| 8. Persistent save/load | done | `PersistenceTest` (11); saved-and-resumed run equals uninterrupted run |
| 9. Automated tests | done | 91 tests, all passing locally and in CI |
| 10. Repeatable divergence demo | done | `./gradlew -PcoreOnly :core:demo`; results below |

### What exists

- **`core/`**: the engine-independent simulation, pure Java with no runtime
  dependencies.
  - The world model, with stateless seeded randomness.
  - An event log with causal links.
  - World actions for hosts.
  - Environment: seasons, gravity anomalies, deforestation feedback, storms.
  - Ecosystem: timber, forage, herbivores, migration, reclamation.
  - Civilization: needs, a utility planner with explained decisions,
    labour, building style rules, construction, upkeep, trade, outposts,
    memory and abandonment.
  - Versioned crash-safe persistence.
  - The `evolve` CLI: demo, run, resume, inspect, why, bench and replay.
- **`mod/`**: the Fabric adapter for Minecraft 26.1.2 (26 files).
  - Anchors the grid to chunks and reads the real terrain.
  - Turns simulation actions into budgeted, idempotent block jobs: felling,
    planting, procedural buildings, decay, roads.
  - Surveys real forests back into the model and applies region gravity to
    players.
  - Adds `/evolve` commands and announces major events.
- **CI** (`.github/workflows/build.yml`): tests, demo and benchmarks; builds
  the mod jar; mirrors logs and the jar to the branch
  `ci-logs/<branch>`.

### Verification

**Tests.** 91 JUnit tests: integration 11, world generation 9, ecosystem 12,
environment 10, civilization 28, persistence 17, randomness 4. All pass
locally (JDK 21) and in GitHub Actions (JDK 25).

**Determinism.** The same seed gives the same history. A world saved on day
70 and resumed produces exactly the state of the uninterrupted run. The
benchmark entity counts match exactly between this machine (Java 21) and
GitHub's runner (Java 25): for example 8,942 people, 3,405 buildings and
12,473 events in the 256 x 256 world.

**Mod build.** GitHub Actions compiles the mod against Minecraft 26.1.2 with
Loom 1.19.3, Fabric Loader 0.20.1 and Fabric API 0.155.3+26.1.2. The jar is
about 219 KB, with 34 mod classes and 62 core classes, and its
`fabric.mod.json` depends on Minecraft `~26.1.2` and Java 25. Every Minecraft
and Fabric call was first checked against the 26.1.2 sources of Fabric API,
NeoForge and Carpet (`mod/API_NOTES.md`).

**Demo** (seed 20261029, 240 days). Same world and code; only the forest
around Oakridge differs on day 0.

| | Abundant timber | Depleted forest |
|---|---|---|
| Strategy history | EXPAND, then OUTPOST from day 119 | OUTPOST day 1, TRADE day 59, CONSERVE day 79, EXPAND day 99, OUTPOST day 119 |
| Builds in | timber (36 timber buildings) | stone (22 timber, 14 stone) |
| Timber harvested | about 4,100 logs | about 1,400 logs |
| Trade routes opened | 0 | 2 |
| Outposts founded | 4 | 4 |
| Home region forest cover | 0.77 → 0.24 | 0.20 → 0.23 |
| Home region fertility | 1.29 → 1.21 | 1.29 → 1.13 (erosion) |

Each strategic turn can be traced. For the depleted town the chain is
`SETTLEMENT_FOUNDED` → `STRATEGY_CHANGED` (scores EXPAND 0.27, CONSERVE
0.58, TRADE 0.57, OUTPOST 0.79; scarcity 0.83) → `OUTPOST_FOUNDED`.

The seed sweep runs 12 worlds with both starting conditions. The two towns'
histories diverged in 12 of 12. Conservation appears in 6 of 12 depleted
worlds and none of the abundant ones; trade in 5 of 12 and none. Mean final
population is 62 for abundant worlds and 45 for depleted ones.

In the gravity check, mean building height is 1.77 storeys at gravity 0.6,
1.55 at 1.0 and 1.00 at 1.6.

**Benchmarks** (this machine, 4 cores; 100 timed days after 20 warm-up days):

| World | Towns | People | ms per day | Heap | Save | Save / load |
|---|---|---|---|---|---|---|
| 32 x 32 | 4 | 151 | 0.75 | 0.7 MB | 173 KB | 21 / 58 ms |
| 64 x 64 | 16 | 497 | 1.48 | 0.7 MB | 682 KB | 20 / 36 ms |
| 128 x 128 | 63 | 2,241 | 4.66 | 2.8 MB | 2.7 MB | 200 / 414 ms |
| 256 x 256 | 248 | 8,942 | 24.15 | 9.7 MB | 8.7 MB | 203 / 350 ms |

In game the default is one simulated day every 600 ticks (30 s), on a
32 x 32 grid.

### Fixed during integration

- An outpost in a low-gravity region planned a four-storey hall whose
  materials exceeded its storage, so it never built one. Plans now fit in
  storage; there is a regression test.
- Towns flipped between CONSERVE and EXPAND on score differences of about
  0.02. A new strategy is now kept for at least 20 days.
- A town's first decision had no recorded cause. It now links to the founding.
- The demo's neighbour town was placed in barren mountains and starved. The
  scenario and the mod's `/evolve start` now share a livable-site rule.
- A town's roads to its own outposts were counted as trade routes. They
  are now reported as supply roads.

### Known issues and limits

- The mod has not run in a live game yet. Block placement, structure
  appearance and command output are untested in game. The tick handler
  and every block job catch exceptions, so a bug is logged rather than
  crashing the server.
- Populations are not shown as villagers, and simulated wildlife does not
  spawn vanilla animals.
- Region gravity applies to players only.
- Storms make up about half of all recorded events. In game only storms near
  players matter; the event feed may need filtering.
- One simulated area (32 x 32 chunks) per world, in the overworld only.
- Trade needs a partner with spare timber, so it is rare when neighbours
  are themselves short.

### Next steps

1. Run the mod in a real 26.1.2 world (`docs/MOD_GUIDE.md`). Optionally, add
   an automated in-game test in CI using Fabric's gametest server; that needs
   the owner's consent to accept the Minecraft EULA for the test run.
2. Villager avatars for settlement population; wildlife counts reflected by
   vanilla animals.
3. More environment rules (atmosphere, region-specific material strength)
   and predators.
4. Lower-detail simulation for distant regions, and grids larger than one
   32 x 32 area, measured with the benchmark.
