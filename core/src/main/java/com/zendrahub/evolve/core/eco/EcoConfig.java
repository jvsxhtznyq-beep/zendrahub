package com.zendrahub.evolve.core.eco;

/** Tuning for the ecosystem rules. Rates are per day at growth factor 1 (fertility 1, summer). */
public final class EcoConfig {
    /** Logistic growth rate of standing timber. */
    public double timberGrowthRate = 0.025;
    /** Seed rain into a cell whose neighbours are fully forested, as a share of capacity x growth rate. */
    public double seedRainShare = 0.05;
    /** Background regrowth from the soil seed bank, even with no forest nearby (same units). */
    public double seedBankShare = 0.003;
    /** Share of planted saplings that mature each day. */
    public double saplingMaturation = 0.033;
    /** Logs of standing timber one matured sapling becomes. */
    public double logsPerSapling = 4.0;

    /** Logistic regrowth rate of wild forage. */
    public double forageGrowthRate = 0.12;
    /** Regrowth from roots and seed even when grazed bare, as a share of capacity. */
    public double forageSeedShare = 0.02;

    /** Forage one animal eats per day, in food units. */
    public double herbivoreEatRate = 0.8;
    /** Births per animal per day when fully fed, in ideal and uncrowded habitat, in summer. */
    public double herbivoreBirthRate = 0.06;
    /** Deaths per animal per day from age and predators. */
    public double herbivoreBaseDeath = 0.01;
    /** Extra deaths per animal per day when getting no food at all (scales with the shortfall). */
    public double herbivoreStarvationDeath = 0.10;
    /** Extra deaths per animal above habitat capacity per day. */
    public double herbivoreCrowdingDeath = 0.05;
    /** Animals a cell's habitat supports per food unit of forage capacity, at ideal habitat. */
    public double herbivoresPerForage = 0.025;
    /** Habitat quality of a cell with no forest cover. */
    public double habitatFloor = 0.3;
    /** Forest cover at and above which habitat is ideal. */
    public double habitatFullCover = 0.5;
    /** Below this many animals a cell's population dies out. */
    public double extinctionThreshold = 0.01;

    /** Share of a cell's animals that leave per day when hungry or exposed. */
    public double migrationRate = 0.25;
    /** Animals getting less than this share of their food are hungry and move on. */
    public double migrationFedThreshold = 0.7;
    /** Animals in habitat below this quality are exposed and move on. */
    public double migrationHabitatThreshold = 0.5;
    /** A neighbour must be at least this much more attractive to draw migrants. */
    public double migrationAdvantage = 1.1;

    /** Region forest cover, relative to its initial cover, below which its forests count as depleted. */
    public double forestDepletedRatio = 0.5;
    /** ...and above which a depleted region counts as recovered. */
    public double forestRecoveredRatio = 0.8;
    /** Regions that started with less cover than this get no forest events. */
    public double forestEventMinInitialCover = 0.1;
    /** Region herbivores, relative to the initial count, below which wildlife counts as depleted. */
    public double wildlifeDeclineRatio = 0.5;
    /** ...and above which depleted wildlife counts as recovered. */
    public double wildlifeRecoveredRatio = 0.8;
    /** Regions that started with fewer animals than this get no wildlife events. */
    public double wildlifeEventMinInitial = 1.0;

    /** A host survey is reported as an event only if it differs from the model by at least this many logs... */
    public double observationEventMinLogs = 20.0;
    /** ...and by at least this share of the larger of the two values. */
    public double observationEventShare = 0.3;
}
