package com.zendrahub.evolve.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The block-by-block plan of one structure, relative to its anchor (the
 * north-west corner of the footprint at floor height). Placements come out in
 * build order: clearing, foundation, floor, structure, roof, then fittings.
 */
final class Blueprint {

    enum Role { CLEAR, FOUNDATION, FLOOR, STRUCTURE, ROOF, FEATURE }

    record Placement(BlockPos pos, BlockState state, Role role) {
    }

    private final int originX;
    private final int floorY;
    private final int originZ;
    private final List<List<Placement>> byRole = new ArrayList<>();

    Blueprint(int originX, int floorY, int originZ) {
        this.originX = originX;
        this.floorY = floorY;
        this.originZ = originZ;
        for (int i = 0; i < Role.values().length; i++) {
            byRole.add(new ArrayList<>());
        }
    }

    void put(int dx, int dy, int dz, BlockState state, Role role) {
        byRole.get(role.ordinal()).add(
                new Placement(new BlockPos(originX + dx, floorY + dy, originZ + dz), state, role));
    }

    void clear(int dx, int dy, int dz) {
        put(dx, dy, dz, Blocks.AIR.defaultBlockState(), Role.CLEAR);
    }

    /** Absolute placement, for foundations that follow the terrain. */
    void putAbsolute(BlockPos pos, BlockState state, Role role) {
        byRole.get(role.ordinal()).add(new Placement(pos, state, role));
    }

    List<Placement> placements() {
        List<Placement> all = new ArrayList<>();
        for (List<Placement> list : byRole) {
            all.addAll(list);
        }
        return all;
    }
}
