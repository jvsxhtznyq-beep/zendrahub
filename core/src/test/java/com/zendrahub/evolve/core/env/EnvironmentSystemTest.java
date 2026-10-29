package com.zendrahub.evolve.core.env;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.WorldGen;
import com.zendrahub.evolve.core.WorldGenOptions;
import com.zendrahub.evolve.core.action.WorldAction;
import com.zendrahub.evolve.core.eco.EcoWorlds;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentSystemTest {
    private final SimConfig cfg = SimConfig.defaults();

    /** A stormy world with a settlement owning one timber and one stone building, side by side. */
    private static WorldState stormyTown() {
        WorldState s = EcoWorlds.uniform(4, 4, Biome.FOREST, 1.0, 1.0, 1.0, 0.002,
                EnvironmentProfile.STORMINESS_MAX);
        GridPos home = new GridPos(1, 1);
        s.settlements.add(new Settlement(1, "Testford", -1, home, 0));
        addBuilding(s, Material.TIMBER, 1, true);
        addBuilding(s, Material.STONE, 1, true);
        return s;
    }

    private static Building addBuilding(WorldState s, Material material, int stories, boolean complete) {
        Building b = new Building(s.nextBuildingId(), 1, BuildingType.HOUSE, material, stories,
                new GridPos(1, 1), 0);
        if (complete) {
            b.builtDay = 0;
            b.progress = 1;
        }
        s.buildings.add(b);
        return b;
    }

    /** Runs day by day until the first storm; returns that day's events. */
    private List<SimEvent> runUntilStorm(WorldState s, EnvironmentSystem env) {
        for (int d = 0; d < 200; d++) {
            s.day++;
            env.step(new SimContext(s, cfg));
            int today = s.day;
            List<SimEvent> storms = s.events.filter(e -> e.day() == today && e.type() == EventType.STORM);
            if (!storms.isEmpty()) {
                return s.events.filter(e -> e.day() == today);
            }
            s.pendingActions.clear();
        }
        throw new AssertionError("no storm in 200 days at maximum storminess");
    }

    @Test
    void stormsHitTimberHarderThanStone() {
        WorldState s = stormyTown();
        Building timber = s.building(1);
        Building stone = s.building(2);
        Building unfinished = addBuilding(s, Material.TIMBER, 1, false);
        Building abandoned = addBuilding(s, Material.TIMBER, 1, true);
        abandoned.abandoned = true;

        List<SimEvent> today = runUntilStorm(s, new EnvironmentSystem());

        double timberLoss = 1.0 - timber.condition;
        double stoneLoss = 1.0 - stone.condition;
        assertTrue(stoneLoss > 0, "stone is damaged too");
        assertTrue(timberLoss > stoneLoss, "but timber is damaged more: " + timberLoss + " vs " + stoneLoss);
        assertEquals(1.0, unfinished.condition, "buildings under construction are skipped");
        assertEquals(1.0, abandoned.condition, "abandoned buildings are skipped");

        SimEvent storm = today.stream().filter(e -> e.type() == EventType.STORM).findFirst().orElseThrow();
        SimEvent damage = today.stream().filter(e -> e.type() == EventType.BUILDING_DAMAGED).findFirst()
                .orElseThrow();
        assertEquals(1, damage.settlementId());
        assertEquals(storm.id(), damage.causeId(), "the damage report points at its storm");
        assertTrue(damage.message().contains("Testford"));
    }

    @Test
    void tallerBuildingsSufferMore() {
        WorldState s = stormyTown();
        Building low = addBuilding(s, Material.TIMBER, 1, true);
        Building tall = addBuilding(s, Material.TIMBER, 4, true);
        // Same storm, same per-building luck would be needed for an exact comparison, so compare averages.
        double lowLoss = 0;
        double tallLoss = 0;
        EnvironmentSystem env = new EnvironmentSystem();
        for (int i = 0; i < 30; i++) {
            low.condition = 1;
            tall.condition = 1;
            runUntilStorm(s, env);
            lowLoss += 1 - low.condition;
            tallLoss += 1 - tall.condition;
        }
        assertTrue(tallLoss > 1.5 * lowLoss, "four storeys catch far more wind: " + tallLoss + " vs " + lowLoss);
    }

    @Test
    void stormsNeverRuinABuildingOutright() {
        WorldState s = stormyTown();
        EcoWorlds.run(s, cfg, 600, new EnvironmentSystem());
        for (Building b : s.buildings) {
            assertTrue(b.condition >= cfg.env.stormConditionFloor - 1e-12, "condition floor holds: " + b.condition);
        }
        assertEquals(cfg.env.stormConditionFloor, s.building(1).condition, 1e-12,
                "unrepaired timber is worn down to the floor");
    }

    @Test
    void stormsKnockDownTimberAndAskTheHostToShowIt() {
        WorldState s = stormyTown();
        double before = 0;
        for (Cell c : s.cells()) {
            before += c.timber;
        }
        runUntilStorm(s, new EnvironmentSystem());
        double after = 0;
        for (Cell c : s.cells()) {
            after += c.timber;
        }
        assertTrue(after < before, "standing timber fell");
        long felling = s.pendingActions.stream()
                .filter(a -> a instanceof WorldAction.FellTrees f && f.settlementId() == -1)
                .count();
        assertTrue(felling > 0, "fallen trees are queued for the host as natural felling");
        assertTrue(before - after < 0.15 * before, "a single storm takes at most 15% of the region's timber");
    }

    @Test
    void gravityAnomaliesStayWithinBounds() {
        for (long seed : new long[]{1, 2, 3}) {
            WorldState s = WorldGen.generate(seed, WorldGenOptions.defaults());
            List<Region> flux = s.regions.stream().filter(r -> r.env.gravityFlux).toList();
            assertFalse(flux.isEmpty(), "default worlds include gravity anomalies");
            double[] lo = new double[s.regions.size()];
            double[] hi = new double[s.regions.size()];
            java.util.Arrays.fill(lo, Double.MAX_VALUE);
            java.util.Arrays.fill(hi, -Double.MAX_VALUE);
            EnvironmentSystem env = new EnvironmentSystem();
            for (int d = 0; d < 1000; d++) {
                s.day++;
                env.step(new SimContext(s, cfg));
                s.pendingActions.clear();
                for (Region r : flux) {
                    lo[r.id] = Math.min(lo[r.id], r.env.gravity);
                    hi[r.id] = Math.max(hi[r.id], r.env.gravity);
                }
            }
            for (Region r : flux) {
                EnvironmentProfile e = r.env;
                assertTrue(lo[r.id] >= EnvironmentProfile.GRAVITY_MIN && hi[r.id] <= EnvironmentProfile.GRAVITY_MAX);
                assertTrue(lo[r.id] >= e.baseGravity - e.fluxAmplitude - 1e-9);
                assertTrue(hi[r.id] <= e.baseGravity + e.fluxAmplitude + 1e-9);
                assertTrue(hi[r.id] - lo[r.id] > 0.5 * e.fluxAmplitude, r.name + " gravity really varies");
            }
            List<SimEvent> shifts = s.events.ofType(EventType.ENVIRONMENT_SHIFT);
            assertTrue(shifts.stream().anyMatch(e -> e.message().contains("peaks")));
            assertTrue(shifts.stream().anyMatch(e -> e.message().contains("bottoms out")));
            // Two turning points per period; periods are at least 60 days.
            assertTrue(shifts.size() <= flux.size() * (2 * 1000 / 60 + 2), "shift reports are rate-limited");
        }
    }

    @Test
    void stableGravityStaysAtBase() {
        WorldState s = EcoWorlds.uniform(2, 2, Biome.PLAINS, 0.5, 1.3, 1.0, 0.002, 0.0);
        EcoWorlds.run(s, cfg, 100, new EnvironmentSystem());
        assertEquals(1.3, s.regions.get(0).env.gravity, 1e-12);
        assertTrue(s.events.ofType(EventType.ENVIRONMENT_SHIFT).isEmpty());
    }

    @Test
    void deforestationErodesSoilAndInvitesStormsThenHeals() {
        WorldState s = EcoWorlds.uniform(4, 4, Biome.FOREST, 1.0, 1.0, 1.0, 0.002, 0.0);
        Region r = s.regions.get(0);
        EnvironmentProfile e = r.env;
        assertTrue(r.initialForestCover > 0.9);
        r.forestCover = 0; // logged bare; the ecosystem is not running in this test
        EnvironmentSystem env = new EnvironmentSystem();
        EcoWorlds.run(s, cfg, 150, env);
        assertTrue(e.fertility < 0.7 * e.baseFertility, "erosion: fertility " + e.fertility);
        assertTrue(e.storminess > e.baseStorminess + 0.04, "no windbreak: storminess " + e.storminess);
        List<SimEvent> shifts = s.events.ofType(EventType.ENVIRONMENT_SHIFT);
        assertTrue(shifts.stream().anyMatch(ev -> ev.message().startsWith("Deforestation")), "the cause is named");

        r.forestCover = r.initialForestCover; // the forest has grown back
        EcoWorlds.run(s, cfg, 300, env);
        assertEquals(e.baseFertility, e.fertility, 0.02 * e.baseFertility, "soil recovers");
        assertEquals(e.baseStorminess, e.storminess, 0.002, "storms subside");
    }

    @Test
    void lightDeforestationChangesNothing() {
        WorldState s = EcoWorlds.uniform(4, 4, Biome.FOREST, 1.0, 1.0, 1.1, 0.002, 0.03);
        Region r = s.regions.get(0);
        r.forestCover = 0.6 * r.initialForestCover;
        EcoWorlds.run(s, cfg, 200, new EnvironmentSystem());
        assertEquals(1.1, r.env.fertility, 1e-9);
        assertEquals(0.03, r.env.storminess, 1e-9);
    }

    @Test
    void seasonsAreAnnounced() {
        WorldState s = EcoWorlds.uniform(2, 2, Biome.PLAINS, 0.5, 1.0, 1.0, 0.002, 0.0);
        EcoWorlds.run(s, cfg, 40, new EnvironmentSystem());
        List<SimEvent> seasons = s.events.ofType(EventType.SEASON_CHANGED);
        assertEquals(4, seasons.size());
        assertEquals("Summer begins (year 1)", seasons.get(0).message());
        assertEquals(10, seasons.get(0).day());
        assertEquals("Spring begins (year 2)", seasons.get(3).message());
    }

    @Test
    void sameSeedSameWeather() {
        WorldState a = WorldGen.generate(31, WorldGenOptions.defaults());
        WorldState b = WorldGen.generate(31, WorldGenOptions.defaults());
        EcoWorlds.run(a, cfg, 300, new EnvironmentSystem());
        EcoWorlds.run(b, cfg, 300, new EnvironmentSystem());
        assertEquals(a.events.all().stream().map(SimEvent::toString).toList(),
                b.events.all().stream().map(SimEvent::toString).toList());
    }
}
