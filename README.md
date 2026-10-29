# ZendraHub: Project EVOLVE

A Minecraft world that evolves itself. Settlements plan, build, trade and
adapt; forests and wildlife respond to what they take; and regions follow
their own environmental rules (gravity, fertility, wood rot, storms) that
change over time. History comes from these systems interacting, not from
scripted events.

EVOLVE is a **Fabric mod for Minecraft Java 26.1.2** built on an
engine-independent simulation core that runs and is tested without the game.

## Repository layout

| Path | What it is |
|---|---|
| `core/` | The simulation: world model, environment, ecosystem and civilization systems, persistence, CLI, demo, benchmarks. Pure Java 21, no runtime dependencies. |
| `mod/` | The Fabric adapter for Minecraft 26.1.2 (Java 25): maps simulation cells to chunks, turns simulation actions into blocks, reads the world back, adds `/evolve` commands. |
| `docs/ARCHITECTURE.md` | Platform decision, simulation model, every rule, the Minecraft mapping, persistence. |
| `docs/MILESTONE-1.md` | Milestone 1 acceptance criteria and their status. |
| `docs/MOD_GUIDE.md` | Installing and playing the mod. |
| `docs/PROGRESS.md` | Progress log: done, tested, known issues, next steps. |

## Quick start

### Run the simulation without Minecraft

Requires JDK 21 or newer.

```bash
./gradlew -PcoreOnly :core:test
```

```bash
./gradlew -PcoreOnly :core:demo
```

The demo runs the same world twice, once with abundant timber around the
home town and once with a depleted forest, and prints how their strategies,
buildings, forests, wildlife and soil diverge, why (the causal chain of
events) and how the result changes with region gravity and across seeds.

Other commands (`run`, `resume`, `inspect`, `why`, `bench`) are listed by:

```bash
./gradlew -PcoreOnly :core:run --args="help"
```

### Build the mod

Requires JDK 25 and access to the Fabric and Mojang servers.

```bash
./gradlew :mod:build
```

The jar lands in `mod/build/libs/`. GitHub Actions builds it on every push
against the newest Fabric Loader, Fabric API and Loom for the
`minecraft_version` in `gradle.properties`, and attaches it to the run as the
`evolve-mod` artifact. See `docs/MOD_GUIDE.md` to install and play.

## Design principles

- Deterministic, seeded, fixed daily step; a saved world resumes exactly.
- Subsystems talk only through shared state, an extraction interface and
  events; every decision is rule-based, scored and explained.
- Bounded work per step and per tick; benchmarks measure, not guess.
- Versioned, atomic saves with backup recovery and validated loading.
