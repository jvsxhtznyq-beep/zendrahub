package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimConfig;
import com.zendrahub.evolve.core.model.Biome;
import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.WorldState;
import org.junit.jupiter.api.Test;

import static com.zendrahub.evolve.core.civ.TestWorlds.HOME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingStyleTest {

    @Test
    void gravityCapsStoreys() {
        assertEquals(4, BuildingStyle.storeys(0.6, Material.TIMBER, 0, BuildingType.HOUSE));
        assertEquals(2, BuildingStyle.storeys(1.0, Material.TIMBER, 0, BuildingType.HOUSE));
        assertEquals(1, BuildingStyle.storeys(1.8, Material.TIMBER, 0, BuildingType.HOUSE));
        // Heavy stone in high gravity stays on the ground; storm memories keep buildings low.
        assertEquals(1, BuildingStyle.storeys(1.5, Material.STONE, 0, BuildingType.HOUSE));
        assertEquals(2, BuildingStyle.storeys(0.6, Material.TIMBER, 0.8, BuildingType.HOUSE));
        assertEquals(1, BuildingStyle.storeys(0.5, Material.TIMBER, 0, BuildingType.FARM));
    }

    @Test
    void lowGravityTownsBuildTallerHouses() {
        int low = starterHouseStoreys(0.6);
        int normal = starterHouseStoreys(1.0);
        int high = starterHouseStoreys(1.8);
        assertTrue(low > normal && normal > high, low + " / " + normal + " / " + high);
    }

    private static int starterHouseStoreys(double gravity) {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.abundantForest();
        TestWorlds.env(st, gravity, 1.0, 0.002);
        Settlement town = TestWorlds.found(new CivilizationSystem(), st, cfg, "Oakridge", HOME, 20);
        for (Building b : st.buildings) {
            if (b.settlementId == town.id && b.type == BuildingType.HOUSE) {
                return b.stories;
            }
        }
        throw new AssertionError("no starter house");
    }

    @Test
    void gravitySlowsQuarryingLoggingAndStoneWork() {
        CivConfig cfg = new CivConfig();
        assertTrue(Labor.stonePerWorker(cfg, 0.8, 1.6) < Labor.stonePerWorker(cfg, 0.8, 1.0));
        assertEquals(Labor.stonePerWorker(cfg, 0.8, 1.0) / 1.6, Labor.stonePerWorker(cfg, 0.8, 1.6), 1e-12);
        assertTrue(Construction.laborDays(BuildingType.HOUSE, Material.STONE, 1, 1.6)
                > Construction.laborDays(BuildingType.HOUSE, Material.STONE, 1, 1.0));

        SimConfig sim = SimConfig.defaults();
        WorldState heavy = TestWorlds.abundantForest();
        TestWorlds.env(heavy, 1.6, 1.0, 0.002);
        WorldState light = TestWorlds.abundantForest();
        Settlement a = TestWorlds.found(new CivilizationSystem(), heavy, sim, "Heavy", HOME, 20);
        Settlement b = TestWorlds.found(new CivilizationSystem(), light, sim, "Light", HOME, 20);
        assertTrue(Labor.logsPerWorker(TestWorlds.ctx(heavy, sim), a, new Ledger(heavy))
                < Labor.logsPerWorker(TestWorlds.ctx(light, sim), b, new Ledger(light)));
    }

    @Test
    void fastRottingWoodTurnsBuildersAwayFromTimber() {
        Material dry = foundedMaterial(0.001, Biome.FOREST);
        Material rotting = foundedMaterial(0.028, Biome.FOREST);
        assertEquals(Material.TIMBER, dry);
        assertNotEquals(Material.TIMBER, rotting);
    }

    @Test
    void wetSoilOffersMudBrickWhereWoodRots() {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.uniform(24, 24, Biome.WETLAND, 0.2);
        TestWorlds.env(st, 1.0, 1.0, 0.02);
        Settlement town = TestWorlds.found(new CivilizationSystem(), st, cfg, "Mirefen", HOME, 20);
        BuildingStyle.Choice choice = BuildingStyle.choose(TestWorlds.ctx(st, cfg), town, BuildingType.HOUSE);
        assertEquals(Material.MUD_BRICK, choice.material(), String.join("\n", choice.reasons()));
        assertEquals(4, choice.reasons().size());
    }

    private static Material foundedMaterial(double woodDecay, Biome biome) {
        SimConfig cfg = SimConfig.defaults();
        WorldState st = TestWorlds.uniform(24, 24, biome, 0.9);
        TestWorlds.env(st, 1.0, 1.0, woodDecay);
        Settlement town = TestWorlds.found(new CivilizationSystem(), st, cfg, "Oakridge", HOME, 20);
        return town.preferredMaterial;
    }
}
