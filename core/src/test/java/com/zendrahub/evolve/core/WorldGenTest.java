package com.zendrahub.evolve.core;

import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.env.RegionArchetype;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGenTest {

    @Test
    void sameSeedGivesTheSameWorld() {
        WorldState a = WorldGen.generate(42, WorldGenOptions.defaults());
        WorldState b = WorldGen.generate(42, WorldGenOptions.defaults());
        for (int i = 0; i < a.cells().length; i++) {
            Cell ca = a.cells()[i];
            Cell cb = b.cells()[i];
            assertEquals(ca.biome, cb.biome);
            assertEquals(ca.timber, cb.timber, 0.0);
            assertEquals(ca.forage, cb.forage, 0.0);
            assertEquals(ca.herbivores, cb.herbivores, 0.0);
            assertEquals(ca.regionId, cb.regionId);
        }
        for (int i = 0; i < a.regions.size(); i++) {
            Region ra = a.regions.get(i);
            Region rb = b.regions.get(i);
            assertEquals(ra.name, rb.name);
            assertEquals(ra.env.gravity, rb.env.gravity, 0.0);
            assertEquals(ra.env.fertility, rb.env.fertility, 0.0);
            assertEquals(ra.env.woodDecay, rb.env.woodDecay, 0.0);
            assertEquals(ra.env.storminess, rb.env.storminess, 0.0);
        }
        assertEquals(a.events.all().get(0).message(), b.events.all().get(0).message());
    }

    @Test
    void differentSeedsGiveDifferentWorlds() {
        WorldState a = WorldGen.generate(1, WorldGenOptions.defaults());
        WorldState b = WorldGen.generate(2, WorldGenOptions.defaults());
        int differing = 0;
        for (int i = 0; i < a.cells().length; i++) {
            if (a.cells()[i].biome != b.cells()[i].biome) {
                differing++;
            }
        }
        assertTrue(differing > a.cells().length / 5, "terrain layouts differ: " + differing + " cells");
        Set<String> namesA = new HashSet<>();
        a.regions.forEach(r -> namesA.add(r.name));
        assertTrue(b.regions.stream().anyMatch(r -> !namesA.contains(r.name)));
    }

    @Test
    void defaultWorldsHaveVariedRegionsAndATemperateCentre() {
        for (long seed = 1; seed <= 10; seed++) {
            WorldState s = WorldGen.generate(seed, WorldGenOptions.defaults());
            Set<RegionArchetype> kinds = EnumSet.noneOf(RegionArchetype.class);
            for (Region r : s.regions) {
                RegionArchetype a = RegionArchetype.fromName(r.name);
                assertNotNull(a, "region names end with their kind: " + r.name);
                kinds.add(a);
            }
            assertTrue(kinds.size() >= 3, "seed " + seed + " has " + kinds.size() + " kinds of region");
            assertTrue(s.regions.stream().anyMatch(r -> r.env.gravityFlux), "seed " + seed + " has an anomaly");
            Region centre = s.regionAt(s.width / 2, s.height / 2);
            assertEquals(RegionArchetype.TEMPERATE, RegionArchetype.fromName(centre.name));
            assertEquals(1.0, centre.env.gravity, 0.0, "the starting region has normal gravity");
            Set<String> names = new HashSet<>();
            s.regions.forEach(r -> assertTrue(names.add(r.name), "unique name " + r.name));
        }
    }

    @Test
    void regionProfilesFollowTheirArchetypes() {
        WorldState s = WorldGen.generate(5, WorldGenOptions.defaults());
        for (Region r : s.regions) {
            RegionArchetype a = RegionArchetype.fromName(r.name);
            assertEquals(a.fertility, r.env.baseFertility, a.fertility * 0.1 + 1e-9);
            assertEquals(a.woodDecay, r.env.baseWoodDecay, a.woodDecay * 0.1 + 1e-12);
            assertEquals(a.storminess, r.env.baseStorminess, a.storminess * 0.1 + 1e-12);
            assertEquals(a.gravity, r.env.baseGravity, a.gravity * 0.04 + 1e-9);
            assertEquals(a.fluxAmplitude > 0, r.env.gravityFlux);
        }
    }

    @Test
    void regionsTileTheGridExactly() {
        WorldState s = WorldGen.generate(3, new WorldGenOptions(30, 20, 8));
        int total = 0;
        for (Region r : s.regions) {
            total += r.cellCount();
        }
        assertEquals(30 * 20, total);
        for (Cell c : s.cells()) {
            Region r = s.region(c.regionId);
            assertNotNull(r);
            assertTrue(r.contains(c.x, c.z));
        }
    }

    @Test
    void initialEcologyMatchesCapacities() {
        WorldState s = WorldGen.generate(11, WorldGenOptions.defaults());
        for (Cell c : s.cells()) {
            double capacity = Ecology.timberCapacity(c);
            assertTrue(c.timber >= 0.6 * capacity - 1e-9 && c.timber <= capacity + 1e-9,
                    "timber starts at 60-100% of capacity");
            assertTrue(c.forage >= 0 && c.forage <= Ecology.forageCapacity(c) + 1e-9);
            assertTrue(c.herbivores >= 0);
            if (c.biome == Biome.WATER) {
                assertEquals(0, c.timber);
                assertEquals(0, c.herbivores);
            }
        }
        for (Region r : s.regions) {
            assertEquals(r.forestCover, r.initialForestCover, 0.0);
            assertEquals(r.herbivores, r.initialHerbivores, 0.0);
            assertTrue(r.initialHerbivores > 0, r.name + " has wildlife");
        }
        assertEquals(1, s.events.ofType(EventType.WORLD_CREATED).size());
        assertEquals(0, s.day);
    }

    @Test
    void biomeMixIsVaried() {
        WorldState s = WorldGen.generate(8, WorldGenOptions.defaults());
        Map<Biome, Integer> counts = new EnumMap<>(Biome.class);
        for (Cell c : s.cells()) {
            counts.merge(c.biome, 1, Integer::sum);
        }
        assertTrue(counts.size() >= 6, "most biomes appear: " + counts);
        double forest = counts.getOrDefault(Biome.FOREST, 0) / (double) s.cells().length;
        assertTrue(forest > 0.2 && forest < 0.6, "forest share " + forest);
    }

    @Test
    void blankWorldsHaveRegionsButWaitForTheHost() {
        WorldState s = WorldGen.blank(4, new WorldGenOptions(16, 16, 8));
        assertEquals(4, s.regions.size());
        for (Cell c : s.cells()) {
            assertEquals(Biome.PLAINS, c.biome);
            assertEquals(0, c.timber);
            assertEquals(0, c.herbivores);
        }
        assertEquals(1, s.events.ofType(EventType.WORLD_CREATED).size());

        Cell c = s.cell(3, 3);
        WorldGen.populateCell(c, Biome.FOREST, 0.5);
        assertEquals(Biome.FOREST, c.biome);
        assertEquals(0.5 * Biome.FOREST.timberCapacity, c.timber, 1e-9);
        assertTrue(c.forage > 0 && c.herbivores > 0);
        WorldGen.populateCell(c, Biome.FOREST, 7.0);
        assertEquals(Biome.FOREST.timberCapacity, c.timber, 1e-9, "shares are clamped");
        WorldGen.populateCell(c, Biome.HILLS, Double.NaN);
        assertEquals(0, c.timber, "NaN counts as no timber");
        WorldGen.populateCell(c, Biome.WATER, 1.0);
        assertEquals(0, c.herbivores, "no animals live in water");
    }

    @Test
    void tinyWorldsWork() {
        WorldState s = WorldGen.generate(1, new WorldGenOptions(1, 1, 8));
        assertEquals(1, s.regions.size());
        assertEquals(RegionArchetype.TEMPERATE, RegionArchetype.fromName(s.regions.get(0).name));
    }
}
