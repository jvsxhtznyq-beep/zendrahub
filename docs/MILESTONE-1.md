# Milestone 1: a small world where all three systems interact

Goal: a small but real prototype in which civilization decisions, ecosystem
changes and environmental rules form a genuine causal chain, testable without
Minecraft and visible in Minecraft.

## Acceptance criteria

| # | Criterion | How it is met | How it is verified |
|---|---|---|---|
| 1 | One procedurally generated region | `WorldGen.generate`: 32 x 32 cells (chunks) from seeded noise, split into 8 x 8-cell regions drawn from environment archetypes. In game the grid is built from the real terrain around the player. | `WorldGenTest` (determinism per seed, variety across seeds) |
| 2 | One settlement with population and resources | `Settlement`: population, food, timber, stone, buildings, labour, memory. | civilization tests |
| 3 | A working timber and food economy | Logging, quarrying, gathering, hunting and fields, all extracted through `Ecology`; consumption, storage caps, construction costs. | civilization tests; demo |
| 4 | A small simulated ecosystem | Logistic forest growth with seed rain and planted saplings, forage, herbivores with habitat, starvation and migration, reclamation of ruins. | ecosystem tests |
| 5 | One environmental rule that affects behaviour | Four: gravity (hauling, quarrying, building height, player gravity in game), fertility (all plant growth), wood decay (material choice, upkeep), storms (damage, building style). Rules drift: deforestation erodes soil and invites storms; anomalous regions have oscillating gravity. | environment tests; `IntegrationTest.gravityShapesConstruction`; demo gravity check |
| 6 | A settlement decision that responds to changing conditions | Utility-scored strategy (expand, conserve, trade, outpost) re-evaluated on a schedule and on shortages, with every score and reason recorded. | civilization tests; `IntegrationTest.depletedForestsPushTownsOffTheExpansionPath` |
| 7 | Visible or inspectable world state | In game: structures, felled and replanted forest, roads, decay, gravity; `/evolve` commands. Headless: `evolve inspect`, `evolve why`, the replay viewer page. | demo output; replay viewer |
| 8 | Persistent save/load | Versioned JSON, atomic writes, backup recovery, validated loading; in game `<world>/evolve/state.json`. | `PersistenceTest`; `IntegrationTest.savedAndResumedRunMatchesUninterruptedRun` |
| 9 | Automated tests for the core interactions | JUnit suites per subsystem plus cross-system integration tests. | `./gradlew -PcoreOnly :core:test` |
| 10 | Repeatable demonstration of divergent outcomes | `evolve demo`: same seed, same code, abundant vs. depleted forest; gravity comparison; seed sweep. | `./gradlew -PcoreOnly :core:demo` |

## Status

See `docs/PROGRESS.md` for what has been verified, with the actual numbers.
