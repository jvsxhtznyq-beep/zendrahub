package com.zendrahub.evolve.core.model;

/**
 * The environmental rules in force in one region. Each property changes real
 * behaviour: gravity scales hauling and quarrying effort, caps building height
 * and (in game) sets the gravity of entities in the region; fertility scales
 * every kind of plant growth; wood decay is how fast timber structures rot;
 * storminess is the daily chance of a damaging storm.
 *
 * <p>Current values drift under explicit rules (see EnvironmentSystem) but are
 * always clamped to the bounds below, and relax toward their base values.
 */
public final class EnvironmentProfile {
    public static final double GRAVITY_MIN = 0.4;
    public static final double GRAVITY_MAX = 1.8;
    public static final double FERTILITY_MIN = 0.2;
    public static final double FERTILITY_MAX = 2.0;
    public static final double WOOD_DECAY_MIN = 0.0005;
    public static final double WOOD_DECAY_MAX = 0.03;
    public static final double STORMINESS_MIN = 0.0;
    public static final double STORMINESS_MAX = 0.25;

    public double gravity = 1.0;
    public double fertility = 1.0;
    public double woodDecay = 0.002;
    public double storminess = 0.02;

    public double baseGravity = 1.0;
    public double baseFertility = 1.0;
    public double baseWoodDecay = 0.002;
    public double baseStorminess = 0.02;

    /** Gravity anomaly: when set, gravity oscillates around its base value. */
    public boolean gravityFlux;
    public double fluxAmplitude;
    public double fluxPeriodDays = 60;
    public double fluxPhase;

    public void clamp() {
        gravity = clamp(gravity, GRAVITY_MIN, GRAVITY_MAX);
        fertility = clamp(fertility, FERTILITY_MIN, FERTILITY_MAX);
        woodDecay = clamp(woodDecay, WOOD_DECAY_MIN, WOOD_DECAY_MAX);
        storminess = clamp(storminess, STORMINESS_MIN, STORMINESS_MAX);
        baseGravity = clamp(baseGravity, GRAVITY_MIN, GRAVITY_MAX);
        baseFertility = clamp(baseFertility, FERTILITY_MIN, FERTILITY_MAX);
        baseWoodDecay = clamp(baseWoodDecay, WOOD_DECAY_MIN, WOOD_DECAY_MAX);
        baseStorminess = clamp(baseStorminess, STORMINESS_MIN, STORMINESS_MAX);
    }

    /** Sets both the base and the current value of every property. */
    public EnvironmentProfile set(double gravity, double fertility, double woodDecay, double storminess) {
        this.gravity = this.baseGravity = gravity;
        this.fertility = this.baseFertility = fertility;
        this.woodDecay = this.baseWoodDecay = woodDecay;
        this.storminess = this.baseStorminess = storminess;
        clamp();
        return this;
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
