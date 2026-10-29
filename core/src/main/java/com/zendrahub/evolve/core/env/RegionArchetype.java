package com.zendrahub.evolve.core.env;

/**
 * Templates for a region's environmental rules. World generation picks one per
 * region and jitters its values, so regions of one kind are similar but not
 * identical. Biases nudge terrain generation (higher, wetter, drier ground) so
 * the land matches its rules.
 */
public enum RegionArchetype {
    /** Ordinary conditions; the baseline everything else is compared with. */
    TEMPERATE("Vale", 1.0, 1.0, 0.002, 0.02, 0.0, 0.0, 0.02),
    /** Heavy, windy uplands: hauling and quarrying are hard, buildings stay low. */
    HIGHLANDS("Highlands", 1.3, 0.8, 0.0015, 0.04, 0.0, 0.10, 0.0),
    /** Gravity anomaly, light and fluctuating: tall timber buildings are easy. */
    SKYREACH("Skyreach", 0.6, 1.2, 0.0025, 0.02, 0.2, 0.05, 0.03),
    /** Gravity anomaly, heavy and fluctuating. */
    DEEPWELL("Deepwell", 1.5, 0.9, 0.002, 0.03, 0.15, 0.04, -0.04),
    /** Fertile but damp: wood rots several times faster than elsewhere. */
    ROTMARSH("Rotmarsh", 1.0, 1.2, 0.012, 0.03, 0.0, -0.06, 0.12),
    /** Frequent violent storms. */
    STORMCOAST("Stormcoast", 1.0, 0.9, 0.004, 0.08, 0.0, -0.05, -0.02),
    /** Poor, dry soil and gusty weather; wood keeps well. */
    BADLANDS("Badlands", 1.1, 0.5, 0.001, 0.05, 0.0, 0.0, -0.15);

    /** The word that ends the names of regions of this kind. */
    public final String noun;
    public final double gravity;
    public final double fertility;
    public final double woodDecay;
    public final double storminess;
    /** Gravity oscillation amplitude; zero for stable regions. */
    public final double fluxAmplitude;
    /** Added to terrain elevation noise inside the region. */
    public final double elevationBias;
    /** Added to terrain moisture noise inside the region. */
    public final double moistureBias;

    RegionArchetype(String noun, double gravity, double fertility, double woodDecay, double storminess,
                    double fluxAmplitude, double elevationBias, double moistureBias) {
        this.noun = noun;
        this.gravity = gravity;
        this.fertility = fertility;
        this.woodDecay = woodDecay;
        this.storminess = storminess;
        this.fluxAmplitude = fluxAmplitude;
        this.elevationBias = elevationBias;
        this.moistureBias = moistureBias;
    }

    /** The archetype a region name ends with, or null for names from elsewhere. */
    public static RegionArchetype fromName(String regionName) {
        if (regionName == null) {
            return null;
        }
        for (RegionArchetype a : values()) {
            if (regionName.endsWith(" " + a.noun) || regionName.contains(" " + a.noun + " ")) {
                return a;
            }
        }
        return null;
    }
}
