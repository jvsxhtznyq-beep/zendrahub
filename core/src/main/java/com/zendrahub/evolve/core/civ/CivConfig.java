package com.zendrahub.evolve.core.civ;

/** Tuning for the civilization rules. Rates are per person or per worker per day unless noted. */
public final class CivConfig {
    // People
    /** Food one person eats per day. */
    public double foodPerPerson = 1.0;
    /** Share of the population that works. */
    public double workShare = 0.6;
    /** Outposts are work camps of adults: most of their people work. */
    public double outpostWorkShare = 0.9;
    /** Births per person per day with full food reserves and free housing. */
    public double birthRate = 0.012;
    /** Natural deaths per person per day. */
    public double deathRate = 0.0015;
    /** Share of unfed people who die on a day without food. */
    public double starvationDeathShare = 0.25;
    /** Food reserve a settlement aims to hold, in days of consumption. */
    public double reserveDays = 10;
    /** Extra reserve days a fully remembered famine adds. */
    public double famineReserveBonus = 10;
    /** Firewood burned per person per day, multiplied in winter. */
    public double firewoodPerPerson = 0.3;
    public double winterFirewoodMultiplier = 2.0;
    /** Days of firewood kept in stock before timber goes into construction. */
    public double firewoodReserveDays = 3;

    // Yields per worker per day
    /** Food per farmer at farm suitability 1 and growth factor 1. */
    public double farmYield = 5.0;
    public int workersPerFarm = 3;
    public double gatherYield = 3.0;
    /** Largest share of a cell's forage gathered in one day. */
    public double gatherMaxShare = 0.25;
    /** Animals one hunter takes per day when game is plentiful. */
    public double huntYield = 0.15;
    /** Largest share of a cell's herbivores hunted in one day. */
    public double huntMaxShare = 0.08;
    public double foodPerAnimal = 8.0;
    /** Logs per logger at gravity 1; divided by the square root of gravity. */
    public double logYield = 6.0;
    /** Extra logging yield while the settlement has a lumber camp. */
    public double lumberCampBonus = 0.2;
    /** Stone per quarrier at stone richness 1 and gravity 1; divided by gravity. */
    public double quarryYield = 4.0;
    public double saplingsPerWorker = 8;
    /** Logs one hauler carries per day at gravity 1; divided by gravity. */
    public double haulPerWorker = 15;
    /** Share of a cell's standing timber that selective (conserving) logging takes per day. */
    public double selectiveLoggingShare = 0.05;
    /** Logs a planted sapling is expected to become; used to judge how much to replant. */
    public double logsPerSapling = 4;

    // Geography
    /** Cells around home (square radius) a settlement works. */
    public int workRadius = 2;
    /** Cells around home (square radius) a settlement builds on. */
    public int buildRadius = 1;
    /** Largest share of a cell covered by buildings. */
    public double maxBuiltArea = 0.85;
    /** Storage for each of food, timber and stone without any storage buildings. */
    public double baseStorage = 100;
    /** No two active settlements closer than this (square radius, in cells). */
    public int minSettlementSpacing = 3;

    // Strategy
    public int strategyInterval = 5;
    /** Horizon over which timber supply is compared with demand. */
    public double scarcityHorizonDays = 120;
    /** Bonus the current strategy gets, so settlements don't flip on noise. */
    public double hysteresis = 0.12;
    /** Days a newly adopted strategy is kept before the settlement may change course again. */
    public int strategyMinDays = 20;
    /** Bonus the material a settlement already builds with gets in the style rules. */
    public double materialHysteresis = 0.1;
    /** Timber regrowth rate settlements assume when planning (their belief, not the ecosystem's rule). */
    public double assumedTimberGrowthRate = 0.02;
    public int minPopulationForOutpost = 12;
    public double outpostSettlerShare = 0.25;
    public int outpostMinSettlers = 4;
    public int outpostMaxSettlers = 8;
    public int outpostMinDistance = 4;
    public int outpostMaxDistance = 12;
    /** Standing timber within an outpost's reach below which its site counts as used up. */
    public double outpostMinStanding = 150;
    /** An outpost is kept at least this long unless it fails; founding one is a commitment. */
    public int outpostMinDays = 60;
    /** A town founds at most one outpost in this many days. */
    public int outpostSpacingDays = 30;
    /** Share of an outpost's hands, after food and construction, that log; the rest haul. */
    public double outpostLoggingShare = 0.6;
    /** Haul loss per cell of distance at gravity 1. */
    public double haulLossPerCell = 0.02;
    public double maxHaulLoss = 0.8;
    /** Largest distance, in cells, to a trade partner. */
    public double tradeRange = 16;
    public double tradeMaxPerDay = 25;
    public double timberPriceInFood = 0.6;
    /** Days without a delivery after which a trade route is closed as failed. */
    public int tradeFailDays = 8;
    /** Days before a settlement asks a partner that failed it again. */
    public int tradeRetryDays = 30;
    /** Share of a partner's logging capacity it is expected to spare for trade. */
    public double partnerExportShare = 0.3;
    /** Minimum days between two shortage events of the same kind for one settlement. */
    public int shortageCooldownDays = 10;

    // Memory
    public double memoryHalfLifeDays = 60;
    public int memoryCapacity = 24;

    // Buildings
    /** Condition below which an active settlement repairs a building. */
    public double repairThreshold = 0.7;
    /** How much faster abandoned buildings decay. */
    public double abandonedDecayMultiplier = 4;
    /** Daily condition loss of each material, as a multiple of the region's wood decay. */
    public double timberDecayFactor = 1.0;
    public double mudBrickDecayFactor = 0.5;
    public double stoneDecayFactor = 0.1;
}
