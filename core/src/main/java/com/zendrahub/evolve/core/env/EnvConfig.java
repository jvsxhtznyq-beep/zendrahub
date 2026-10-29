package com.zendrahub.evolve.core.env;

/** Tuning for the environment rules. */
public final class EnvConfig {
    /** Fraction of the gap to the base value closed per day when nothing pushes a property. */
    public double relaxRate = 0.02;

    /** Region forest cover, relative to its initial cover, below which deforestation feedback starts. */
    public double deforestationThreshold = 0.5;
    /** Regions that started with less forest cover than this get no deforestation feedback. */
    public double deforestationMinInitialCover = 0.15;
    /** Share of base fertility lost to soil erosion when the region's forest is entirely gone. */
    public double erosionMaxFertilityLoss = 0.4;
    /** Storminess added when the region's forest (its windbreak) is entirely gone. */
    public double windbreakMaxStormGain = 0.06;
    /** Fraction of the gap to the feedback target closed per day. */
    public double feedbackRate = 0.02;

    /** Chance that a storm reaches any given cell of its region. */
    public double stormCellChance = 0.35;
    /** Share of a reached cell's standing timber a storm knocks down, at least... */
    public double stormMinTimberLoss = 0.05;
    /** ...and at most. */
    public double stormMaxTimberLoss = 0.15;
    /** Typical condition loss of a fully vulnerable building in a full-strength storm. */
    public double stormBuildingDamage = 0.12;
    /** Storms never push a building below this condition; ruin comes from neglect, not one storm. */
    public double stormConditionFloor = 0.05;
    /** Storm vulnerability by material: timber worst, mud brick middling, stone least. */
    public double timberVulnerability = 1.0;
    public double mudBrickVulnerability = 0.6;
    public double stoneVulnerability = 0.25;
    /** Extra vulnerability per storey above the first. */
    public double storeyVulnerability = 0.25;

    /** A property change is reported when the value crosses a multiple of its band width. */
    public double gravityShiftBand = 0.15;
    public double fertilityShiftBand = 0.1;
    public double stormShiftBand = 0.02;
    public double woodDecayShiftBand = 0.004;
}
