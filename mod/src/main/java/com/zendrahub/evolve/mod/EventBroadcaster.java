package com.zendrahub.evolve.mod;

import com.zendrahub.evolve.core.event.EventLog;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.event.SimEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Announces the turning points of the world's history in chat as they happen. */
final class EventBroadcaster {
    private static final Set<EventType> ANNOUNCED = EnumSet.of(
            EventType.SETTLEMENT_FOUNDED, EventType.OUTPOST_FOUNDED,
            EventType.SETTLEMENT_ABANDONED, EventType.OUTPOST_ABANDONED,
            EventType.STRATEGY_CHANGED, EventType.MATERIAL_CHANGED,
            EventType.TRADE_ROUTE_OPENED, EventType.TRADE_ROUTE_CLOSED,
            EventType.TIMBER_SHORTAGE, EventType.STARVATION,
            EventType.STORM, EventType.ENVIRONMENT_SHIFT,
            EventType.FOREST_DEPLETED, EventType.FOREST_RECOVERED,
            EventType.RECLAIMED);
    private static final int MAX_PER_BATCH = 6;

    private long lastSeenId;

    /** Treat everything recorded so far as already announced (after loading a save). */
    void skipTo(EventLog log) {
        lastSeenId = log.nextId() - 1;
    }

    void publish(MinecraftServer server, EventLog log) {
        List<SimEvent> fresh = log.filter(e -> e.id() > lastSeenId);
        if (fresh.isEmpty()) {
            return;
        }
        lastSeenId = fresh.get(fresh.size() - 1).id();
        int shown = 0;
        int more = 0;
        for (SimEvent e : fresh) {
            if (!ANNOUNCED.contains(e.type())) {
                continue;
            }
            if (shown < MAX_PER_BATCH) {
                McCompat.broadcast(server, format(e));
                shown++;
            } else {
                more++;
            }
        }
        if (more > 0) {
            McCompat.broadcast(server, Component.literal("[EVOLVE] +" + more + " more - /evolve history")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    static Component format(SimEvent e) {
        return Component.literal("[EVOLVE] ").withStyle(ChatFormatting.DARK_GREEN)
                .append(Component.literal("day " + e.day() + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(e.message()).withStyle(color(e.type())));
    }

    static ChatFormatting color(EventType type) {
        return switch (type) {
            case STARVATION, SETTLEMENT_ABANDONED, OUTPOST_ABANDONED, TIMBER_SHORTAGE, FOOD_SHORTAGE,
                 FOREST_DEPLETED, BUILDING_RUINED, TRADE_ROUTE_CLOSED -> ChatFormatting.RED;
            case STORM, ENVIRONMENT_SHIFT, WILDLIFE_DECLINE -> ChatFormatting.LIGHT_PURPLE;
            case STRATEGY_CHANGED, MATERIAL_CHANGED -> ChatFormatting.GOLD;
            case FOREST_RECOVERED, RECLAIMED, WILDLIFE_RECOVERY, REPLANTED -> ChatFormatting.GREEN;
            default -> ChatFormatting.AQUA;
        };
    }
}
