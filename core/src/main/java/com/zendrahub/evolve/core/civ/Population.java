package com.zendrahub.evolve.core.civ;

import com.zendrahub.evolve.core.SimContext;
import com.zendrahub.evolve.core.event.EventType;
import com.zendrahub.evolve.core.model.Cell;
import com.zendrahub.evolve.core.model.MemoryKind;
import com.zendrahub.evolve.core.model.Settlement;

import java.util.Locale;

/** Births, natural deaths and starvation. */
final class Population {
    static final long STREAM_BIRTHS = 301;
    static final long STREAM_DEATHS = 302;
    static final long STREAM_STARVATION = 303;

    private Population() {
    }

    /** Eats today's food; people die when there is not enough. Returns the starvation deaths. */
    static int eat(SimContext ctx, Settlement s, double foodPerDay) {
        CivConfig cfg = ctx.config.civ;
        s.food -= foodPerDay;
        if (s.food >= 0) {
            return 0;
        }
        double unfed = -s.food / cfg.foodPerPerson;
        s.food = 0;
        int deaths = Math.min(s.population,
                ctx.round(unfed * cfg.starvationDeathShare, STREAM_STARVATION, s.id, 0));
        if (deaths > 0) {
            int before = s.population;
            s.population -= deaths;
            Cell home = ctx.state.cell(s.home);
            ctx.emit(EventType.STARVATION, s.id, home.regionId, s.home,
                    String.format(Locale.ROOT, "%d %s starve in %s (%.0f unfed)", deaths,
                            deaths == 1 ? "person" : "people", s.name, unfed));
            Memories.add(ctx, s, MemoryKind.FAMINE, Math.min(1.0, 4.0 * deaths / before),
                    deaths + " starved");
        }
        return deaths;
    }

    /** Births need free housing and food reserves; a few die of old age. */
    static void grow(SimContext ctx, Settlement s, int housing, double reserveTarget) {
        CivConfig cfg = ctx.config.civ;
        int pop = s.population;
        if (pop <= 0) {
            return;
        }
        int births = 0;
        if (housing > pop) {
            double reserveDays = s.food / (pop * cfg.foodPerPerson);
            double factor = Planner.clamp01((reserveDays - 3) / Math.max(1.0, reserveTarget - 3));
            births = Math.min(housing - pop, ctx.round(pop * cfg.birthRate * factor, STREAM_BIRTHS, s.id, 0));
        }
        int deaths = Math.min(pop, ctx.round(pop * cfg.deathRate, STREAM_DEATHS, s.id, 0));
        s.population = pop + births - deaths;
        if (s.population > s.peakPopulation) {
            if (s.population / 10 > s.peakPopulation / 10) {
                Cell home = ctx.state.cell(s.home);
                ctx.emit(EventType.POPULATION_GROWTH, s.id, home.regionId, s.home,
                        String.format(Locale.ROOT, "%s has grown to %d people", s.name, s.population));
            }
            s.peakPopulation = s.population;
        }
    }
}
