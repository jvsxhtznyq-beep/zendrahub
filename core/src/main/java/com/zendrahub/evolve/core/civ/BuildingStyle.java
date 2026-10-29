package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.eco.Ecology;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.EnvironmentProfile;
import com.zendrahub.evolve.core.model.Material;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.model.Strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Style rules: which material a settlement builds with and how tall, derived
 * from what it can reach, its region's environmental rules and its memories.
 * Every choice comes with the scores and factors behind it.
 */
public final class BuildingStyle {

    /** A material and height choice, with the reasons for it. */
    public record Choice(Material material, int stories, List<String> reasons) {
    }

    private BuildingStyle() {
    }

    /** The style a settlement would use for a building of the given type today. */
    public static Choice choose(SimContext ctx, Settlement s, BuildingType type) {
        return choose(ctx, s, type, LocalArea.survey(ctx, s.home, ctx.config.civ.workRadius));
    }

    static Choice choose(SimContext ctx, Settlement s, BuildingType type, LocalArea area) {
        Region region = ctx.state.regionAt(s.home);
        EnvironmentProfile env = region == null ? new EnvironmentProfile() : region.env;
        double gravity = env.gravity;
        double storm = Memories.recall(ctx, s, MemoryKind.STORM_DAMAGE);
        double shortage = Memories.recall(ctx, s, MemoryKind.TIMBER_SHORTAGE);
        List<String> reasons = new ArrayList<>();
        if (type == BuildingType.FARM) {
            reasons.add("fields need only timber fencing");
            return new Choice(Material.TIMBER, 1, reasons);
        }

        double cover = area.cells.isEmpty() ? 0
                : clamp01(area.standingTimber / (area.cells.size() * Ecology.FULL_FOREST_LOGS));
        double timberAvailable = 0.7 * cover + 0.3 * clamp01(s.timber / 100.0);
        double decay = (env.woodDecay - EnvironmentProfile.WOOD_DECAY_MIN)
                / (EnvironmentProfile.WOOD_DECAY_MAX - EnvironmentProfile.WOOD_DECAY_MIN);
        double stormy = env.storminess / EnvironmentProfile.STORMINESS_MAX;
        double conserving = s.strategy == Strategy.CONSERVE ? 0.3 : 0.0;

        double timber = 0.2 + 0.8 * timberAvailable - 0.9 * decay - 0.3 * shortage - 0.15 * storm - conserving;
        double stone = 0.1 + 0.7 * clamp01(area.bestStone) / gravity + 0.3 * shortage + 0.3 * storm + 0.2 * decay;
        double mud = area.mudSoil
                ? 0.35 + 0.2 * decay + 0.2 * (1 - timberAvailable) - 0.4 * stormy
                : Double.NEGATIVE_INFINITY;
        // Builders keep to the material they know unless another is clearly better.
        double habit = s.foundedDay < ctx.day() ? ctx.config.civ.materialHysteresis : 0.0;
        switch (s.preferredMaterial) {
            case TIMBER -> timber += habit;
            case STONE -> stone += habit;
            case MUD_BRICK -> mud += habit;
        }

        Material material = Material.TIMBER;
        double best = timber;
        if (stone > best) {
            material = Material.STONE;
            best = stone;
        }
        if (mud > best) {
            material = Material.MUD_BRICK;
        }
        reasons.add(String.format(Locale.ROOT,
                "timber %.2f (availability %.2f, wood decay %.4f/day, shortage memory %.2f%s)",
                timber, timberAvailable, env.woodDecay, shortage, conserving > 0 ? ", conserving" : ""));
        reasons.add(String.format(Locale.ROOT, "stone %.2f (quarry richness %.2f at gravity %.2f, storm memory %.2f)",
                stone, area.bestStone, gravity, storm));
        reasons.add(area.mudSoil
                ? String.format(Locale.ROOT, "mud brick %.2f (wet or dry soil nearby, storminess %.3f)", mud,
                env.storminess)
                : "mud brick unavailable (no wetland or desert nearby)");
        if (habit > 0) {
            reasons.add(String.format(Locale.ROOT, "+%.2f for staying with %s", habit, s.preferredMaterial));
        }

        int stories = storeys(gravity, material, storm, type);
        reasons.add(String.format(Locale.ROOT, "%d storey%s at gravity %.2f%s", stories, stories == 1 ? "" : "s",
                gravity, storm > 0.5 ? ", kept low after storm damage" : ""));
        return new Choice(material, stories, reasons);
    }

    /**
     * Storeys for a building: about 2.4 / gravity (taller in low gravity), at most
     * two after severe storm memories, a single storey of stone in high gravity.
     */
    public static int storeys(double gravity, Material material, double stormMemory, BuildingType type) {
        if (type == BuildingType.FARM || type == BuildingType.LUMBER_CAMP) {
            return 1;
        }
        int n = (int) Math.round(2.4 / gravity);
        n = Math.max(1, Math.min(4, n));
        if (stormMemory > 0.5) {
            n = Math.min(n, 2);
        }
        if (material == Material.STONE && gravity > 1.3) {
            n = 1;
        }
        if (type == BuildingType.STOREHOUSE || type == BuildingType.TRADING_POST) {
            n = Math.min(n, 2);
        }
        return n;
    }

    static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
