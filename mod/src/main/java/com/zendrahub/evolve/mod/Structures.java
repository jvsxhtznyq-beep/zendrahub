package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.model.Building;
import com.zendrahub.evolve.core.model.BuildingType;
import com.zendrahub.evolve.core.model.GridPos;
import com.zendrahub.evolve.core.model.Region;
import com.zendrahub.evolve.core.model.Settlement;
import com.zendrahub.evolve.core.util.DetRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns simulated buildings into structures: where they stand, what they look
 * like and how they fall apart. Everything here is a pure function of the
 * building's persisted data and the terrain, so re-running it after a restart
 * places exactly the same blocks.
 */
final class Structures {
    /** Regions whose base wood decay is at least this build with rot-resistant dark oak. */
    static final double ROT_PRONE_DECAY = 0.008;
    /** Height of one storey: floor plus three blocks of wall. */
    static final int STOREY = 4;
    /** Blocks cleared above the roof, so trees and overhangs do not hang over a structure. */
    private static final int CLEARANCE = 6;
    private static final long DECAY_STREAM = 401;

    private Structures() {
    }

    // --- palette ------------------------------------------------------------

    static Palette palette(WorldAccess w, Building b) {
        int f = b.type.footprint;
        BlockPos center = new BlockPos(b.anchorX + f / 2, b.anchorY + 1, b.anchorZ + f / 2);
        BiomeMapper.Wood wood = BiomeMapper.classify(w.biome(center)).wood();
        Region region = w.state.regionAt(b.cell);
        if (region != null && region.env.baseWoodDecay >= ROT_PRONE_DECAY) {
            wood = BiomeMapper.Wood.DARK_OAK;
        }
        BlockState sapling = wood.sapling.defaultBlockState();
        return switch (b.material) {
            case TIMBER -> Palette.timber(wood.planks.defaultBlockState(), wood.log.defaultBlockState(),
                    wood.slab.defaultBlockState(), sapling);
            case STONE -> Palette.stone(b.stories, sapling);
            case MUD_BRICK -> Palette.mudBrick(wood.log.defaultBlockState(), sapling);
        };
    }

    // --- site selection -----------------------------------------------------

    /** Outcome of looking for a building site. */
    enum Siting { PLACED, WAIT, NO_SITE }

    /** How many rings of cells around the building's own cell are searched for room. */
    private static final int SITE_SEARCH_RINGS = 2;

    /**
     * Picks a reasonably flat, dry, unoccupied spot for the building in its
     * cell, or in the nearest cells around it when its own is full, and records
     * it as the building's anchor. Waits while the building's cell is not
     * loaded; never builds over another structure.
     */
    static Siting chooseSite(WorldAccess w, Building b) {
        if (!w.cellLoaded(b.cell)) {
            return Siting.WAIT;
        }
        Settlement owner = w.state.settlement(b.settlementId);
        int cx = owner != null ? w.centerX(owner) : w.minBlockX(b.cell) + 8;
        int cz = owner != null ? w.centerZ(owner) : w.minBlockZ(b.cell) + 8;
        for (int ring = 0; ring <= SITE_SEARCH_RINGS; ring++) {
            for (GridPos cell : ringCells(w, b.cell, ring)) {
                if (!w.cellLoaded(cell)) {
                    continue;
                }
                int[] site = bestSiteInCell(w, cell, b.type.footprint, cx, cz);
                if (site != null) {
                    b.setAnchor(site[0], site[1], site[2]);
                    return Siting.PLACED;
                }
            }
        }
        return Siting.NO_SITE;
    }

    /** In-bounds cells at exactly {@code ring} cells (king moves) from the centre, row by row. */
    private static List<GridPos> ringCells(WorldAccess w, GridPos center, int ring) {
        List<GridPos> cells = new ArrayList<>();
        for (int dz = -ring; dz <= ring; dz++) {
            for (int dx = -ring; dx <= ring; dx++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                    continue;
                }
                GridPos n = center.offset(dx, dz);
                if (w.state.inBounds(n)) {
                    cells.add(n);
                }
            }
        }
        return cells;
    }

    /** {x, floorY, z} of the best site fully inside the cell, or null if none is free. */
    private static int[] bestSiteInCell(WorldAccess w, GridPos cell, int f, int centerX, int centerZ) {
        int minX = w.minBlockX(cell);
        int minZ = w.minBlockZ(cell);
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int oz = minZ + 1; oz + f <= minZ + 15; oz += 2) {
            for (int ox = minX + 1; ox + f <= minX + 15; ox += 2) {
                if (w.overlapsFootprint(ox, oz, f, 2)) {
                    continue;
                }
                int[] xs = {ox, ox + f - 1, ox, ox + f - 1, ox + f / 2};
                int[] zs = {oz, oz, oz + f - 1, oz + f - 1, oz + f / 2};
                int[] heights = new int[5];
                int wet = 0;
                int wooded = 0;
                for (int i = 0; i < 5; i++) {
                    heights[i] = w.groundY(xs[i], zs[i]);
                    BlockState ground = w.get(new BlockPos(xs[i], heights[i], zs[i]));
                    if (!ground.getFluidState().isEmpty()) {
                        wet++;
                    }
                    if (w.surfaceY(xs[i], zs[i]) - 1 > heights[i] + 1) {
                        wooded++;
                    }
                }
                int[] sorted = heights.clone();
                Arrays.sort(sorted);
                double dist = Math.hypot(ox + f / 2.0 - centerX, oz + f / 2.0 - centerZ);
                double score = (sorted[4] - sorted[0]) * 4.0 + wet * 50.0 + wooded * 3.0 + dist / 8.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = new int[]{ox, sorted[2], oz};
                }
            }
        }
        return best;
    }

    // --- construction plans -------------------------------------------------

    /**
     * The full plan for a building at its anchor. With {@code siteWork} the
     * plan also fills foundations down to the terrain (reads the world); without
     * it the plan is the structure alone, which is what decay works on.
     */
    static Blueprint plan(WorldAccess w, Building b, boolean siteWork) {
        Palette p = palette(w, b);
        int f = b.type.footprint;
        Blueprint bp = new Blueprint(b.anchorX, b.anchorY, b.anchorZ);
        if (siteWork) {
            for (int dz = 0; dz < f; dz++) {
                for (int dx = 0; dx < f; dx++) {
                    int x = b.anchorX + dx;
                    int z = b.anchorZ + dz;
                    for (int y = w.groundY(x, z) + 1; y < b.anchorY; y++) {
                        bp.putAbsolute(new BlockPos(x, y, z), p.foundation(), Blueprint.Role.FOUNDATION);
                    }
                }
            }
        }
        switch (b.type) {
            case FARM -> farm(bp, f);
            case LUMBER_CAMP -> lumberCamp(bp, p, f, b.stories);
            case TRADING_POST -> enclosed(bp, p, f, b.stories, true, b.type);
            default -> enclosed(bp, p, f, b.stories, false, b.type);
        }
        return bp;
    }

    private static void enclosed(Blueprint bp, Palette p, int f, int stories, boolean openFront, BuildingType type) {
        int roofY = STOREY * stories;
        clearVolume(bp, f, roofY + CLEARANCE);
        floor(bp, f, p.floor());
        for (int k = 0; k < stories; k++) {
            int y0 = 1 + STOREY * k;
            for (int dy = y0; dy < y0 + 3; dy++) {
                for (int dz = 0; dz < f; dz++) {
                    for (int dx = 0; dx < f; dx++) {
                        if (!perimeter(dx, dz, f)) {
                            continue;
                        }
                        boolean corner = (dx == 0 || dx == f - 1) && (dz == 0 || dz == f - 1);
                        if (corner) {
                            bp.put(dx, dy, dz, p.pillar(), Blueprint.Role.STRUCTURE);
                            continue;
                        }
                        boolean south = dz == f - 1;
                        if (k == 0 && south && (openFront || (dx == f / 2 && dy < y0 + 2))) {
                            continue; // doorway or open market front
                        }
                        int along = (dz == 0 || dz == f - 1) ? dx : dz;
                        boolean window = dy == y0 + 1 && along % 2 == 0;
                        bp.put(dx, dy, dz, window ? p.window() : p.wall(), Blueprint.Role.STRUCTURE);
                    }
                }
            }
            if (k < stories - 1) {
                int ceiling = y0 + 3;
                for (int dz = 0; dz < f; dz++) {
                    for (int dx = 0; dx < f; dx++) {
                        bp.put(dx, ceiling, dz, perimeter(dx, dz, f) ? p.pillar() : p.floor(),
                                Blueprint.Role.STRUCTURE);
                    }
                }
            }
        }
        roof(bp, p, f, roofY);
        if (stories >= 3) {
            // Light gravity lets towers rise; crown the tall ones with a spire.
            for (int dy = 1; dy <= 3; dy++) {
                bp.put(f / 2, roofY + dy, f / 2, p.pillar(), Blueprint.Role.ROOF);
            }
            bp.put(f / 2, roofY + 4, f / 2, Blocks.LANTERN.defaultBlockState(), Blueprint.Role.FEATURE);
        }
        fittings(bp, f, roofY, type);
    }

    private static void fittings(Blueprint bp, int f, int roofY, BuildingType type) {
        BlockState lantern = Blocks.LANTERN.defaultBlockState();
        switch (type) {
            case HOUSE -> {
                bp.put(1, 1, 1, lantern, Blueprint.Role.FEATURE);
                bp.put(f - 2, 1, 1, Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.Role.FEATURE);
            }
            case STOREHOUSE -> {
                for (int dx = 1; dx <= f - 2; dx++) {
                    Block box = dx % 2 == 1 ? Blocks.CHEST : Blocks.BARREL;
                    bp.put(dx, 1, 1, box.defaultBlockState(), Blueprint.Role.FEATURE);
                }
                bp.put(f - 2, 1, f - 2, lantern, Blueprint.Role.FEATURE);
            }
            case OUTPOST_HALL -> {
                bp.put(1, 1, 1, Blocks.CHEST.defaultBlockState(), Blueprint.Role.FEATURE);
                bp.put(f - 2, 1, 1, Blocks.BARREL.defaultBlockState(), Blueprint.Role.FEATURE);
                bp.put(f / 2, 1, f / 2, lantern, Blueprint.Role.FEATURE);
                bp.put(f / 2, roofY + 1, f / 2, Blocks.WHITE_BANNER.defaultBlockState(), Blueprint.Role.FEATURE);
            }
            case TRADING_POST -> {
                bp.put(f / 2, 1, f / 2, Blocks.BELL.defaultBlockState(), Blueprint.Role.FEATURE);
                bp.put(1, 1, 1, Blocks.BARREL.defaultBlockState(), Blueprint.Role.FEATURE);
                bp.put(f - 2, 1, 1, Blocks.CHEST.defaultBlockState(), Blueprint.Role.FEATURE);
                bp.put(1, 1, f / 2, Blocks.LECTERN.defaultBlockState(), Blueprint.Role.FEATURE);
            }
            default -> bp.put(1, 1, 1, lantern, Blueprint.Role.FEATURE);
        }
    }

    /** An open-sided shelter on corner posts, with a log pile and a chopping block. */
    private static void lumberCamp(Blueprint bp, Palette p, int f, int stories) {
        int roofY = STOREY * Math.max(1, Math.min(stories, 2));
        clearVolume(bp, f, roofY + CLEARANCE);
        floor(bp, f, p.floor());
        for (int dy = 1; dy < roofY; dy++) {
            bp.put(0, dy, 0, p.pillar(), Blueprint.Role.STRUCTURE);
            bp.put(f - 1, dy, 0, p.pillar(), Blueprint.Role.STRUCTURE);
            bp.put(0, dy, f - 1, p.pillar(), Blueprint.Role.STRUCTURE);
            bp.put(f - 1, dy, f - 1, p.pillar(), Blueprint.Role.STRUCTURE);
        }
        roof(bp, p, f, roofY);
        for (int dx = 1; dx <= f - 2; dx++) {
            bp.put(dx, 1, 1, p.pillar(), Blueprint.Role.FEATURE);
            if (dx < f - 2) {
                bp.put(dx, 2, 1, p.pillar(), Blueprint.Role.FEATURE);
            }
        }
        bp.put(f / 2, 1, f - 2, p.pillar(), Blueprint.Role.FEATURE);
        bp.put(f - 2, 1, f - 2, Blocks.CRAFTING_TABLE.defaultBlockState(), Blueprint.Role.FEATURE);
    }

    /** Irrigated field: farmland around a water source, crops in rows, a path border. */
    private static void farm(Blueprint bp, int f) {
        clearVolume(bp, f, CLEARANCE + 2);
        BlockState[] crops = {Blocks.WHEAT.defaultBlockState(), Blocks.CARROTS.defaultBlockState(),
                Blocks.POTATOES.defaultBlockState()};
        for (int dz = 0; dz < f; dz++) {
            for (int dx = 0; dx < f; dx++) {
                boolean ring = perimeter(dx, dz, f);
                boolean corner = (dx == 0 || dx == f - 1) && (dz == 0 || dz == f - 1);
                if (dx == f / 2 && dz == f / 2) {
                    bp.put(dx, 0, dz, Blocks.WATER.defaultBlockState(), Blueprint.Role.FLOOR);
                } else if (ring) {
                    bp.put(dx, 0, dz, Blocks.COARSE_DIRT.defaultBlockState(), Blueprint.Role.FLOOR);
                    if (corner) {
                        bp.put(dx, 1, dz, Blocks.HAY_BLOCK.defaultBlockState(), Blueprint.Role.FEATURE);
                    }
                } else {
                    bp.put(dx, 0, dz, Blocks.FARMLAND.defaultBlockState(), Blueprint.Role.FLOOR);
                    bp.put(dx, 1, dz, crops[dz % crops.length], Blueprint.Role.FEATURE);
                }
            }
        }
        bp.put(0, 1, f / 2, Blocks.COMPOSTER.defaultBlockState(), Blueprint.Role.FEATURE);
    }

    private static void clearVolume(Blueprint bp, int f, int height) {
        for (int dy = 1; dy <= height; dy++) {
            for (int dz = 0; dz < f; dz++) {
                for (int dx = 0; dx < f; dx++) {
                    bp.clear(dx, dy, dz);
                }
            }
        }
    }

    private static void floor(Blueprint bp, int f, BlockState floor) {
        for (int dz = 0; dz < f; dz++) {
            for (int dx = 0; dx < f; dx++) {
                bp.put(dx, 0, dz, floor, Blueprint.Role.FLOOR);
            }
        }
    }

    private static void roof(Blueprint bp, Palette p, int f, int roofY) {
        for (int dz = 0; dz < f; dz++) {
            for (int dx = 0; dx < f; dx++) {
                bp.put(dx, roofY, dz, p.roof(), Blueprint.Role.ROOF);
            }
        }
    }

    private static boolean perimeter(int dx, int dz, int f) {
        return dx == 0 || dz == 0 || dx == f - 1 || dz == f - 1;
    }

    // --- decay --------------------------------------------------------------

    /**
     * The structure as it looks at the given condition. Each block has a fixed
     * random threshold, so lower condition only ever adds damage: fittings go
     * first, then blocks weather (moss, cracks), then collapse. At condition 0
     * the ruin is gone: the floor turns back to grass and saplings take root.
     */
    static List<Blueprint.Placement> decayPlan(WorldAccess w, Building b, double condition) {
        List<Blueprint.Placement> out = new ArrayList<>();
        Blueprint bp = plan(w, b, false);
        double share = 1.0 - Math.max(0.0, Math.min(1.0, condition));
        BlockState air = Blocks.AIR.defaultBlockState();
        boolean gone = condition <= 0;
        for (Blueprint.Placement p : bp.placements()) {
            if (p.role() == Blueprint.Role.CLEAR || p.role() == Blueprint.Role.FOUNDATION) {
                continue;
            }
            BlockState target;
            if (gone) {
                target = p.role() == Blueprint.Role.FLOOR ? Blocks.GRASS_BLOCK.defaultBlockState() : air;
            } else {
                double r = DetRandom.unit(w.state.seed, DECAY_STREAM, b.id, p.pos().asLong());
                target = decayed(p.state(), p.role(), r, share);
            }
            out.add(new Blueprint.Placement(p.pos(), target, p.role()));
        }
        if (gone) {
            int f = b.type.footprint;
            BlockState sapling = palette(w, b).sapling();
            int[][] spots = {{1, 1}, {f - 2, f - 2}, {f / 2, f / 2}};
            for (int[] s : spots) {
                out.add(new Blueprint.Placement(new BlockPos(b.anchorX + s[0], b.anchorY + 1, b.anchorZ + s[1]),
                        sapling, Blueprint.Role.FEATURE));
            }
        }
        return out;
    }

    static BlockState decayed(BlockState original, Blueprint.Role role, double r, double share) {
        BlockState air = Blocks.AIR.defaultBlockState();
        if (role == Blueprint.Role.FEATURE) {
            return r < share * 1.5 ? air : original;
        }
        if (r >= share) {
            return original;
        }
        boolean collapsed = r < share * 0.5;
        if (role == Blueprint.Role.FLOOR) {
            if (original.is(Blocks.FARMLAND)) {
                return Blocks.DIRT.defaultBlockState();
            }
            if (original.is(Blocks.WATER) || original.is(Blocks.COARSE_DIRT)) {
                return original;
            }
            return collapsed ? Blocks.MOSS_BLOCK.defaultBlockState() : original;
        }
        if (collapsed || original.is(Blocks.GLASS)) {
            return air;
        }
        if (original.is(Blocks.COBBLESTONE)) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (original.is(Blocks.COBBLESTONE_SLAB)) {
            return Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState();
        }
        if (original.is(Blocks.STONE_BRICKS)) {
            return r < share * 0.75 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        if (original.is(Blocks.STONE_BRICK_SLAB)) {
            return Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
        }
        if (original.is(Blocks.MUD_BRICKS)) {
            return Blocks.PACKED_MUD.defaultBlockState();
        }
        if (original.is(Blocks.SMOOTH_STONE) || original.is(Blocks.PACKED_MUD)) {
            return original;
        }
        // Wood (planks, slabs, beams) rots away.
        return air;
    }
}
