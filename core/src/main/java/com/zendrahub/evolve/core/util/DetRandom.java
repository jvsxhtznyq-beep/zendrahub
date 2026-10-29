package com.zendrahub.evolve.core.util;

/**
 * Stateless, counter-based randomness.
 *
 * <p>Every draw is a pure function of the world seed and a list of integer keys
 * (day, stream, entity id, ...). Results therefore do not depend on iteration
 * order or on how many draws other code made, and a saved world resumes exactly
 * where it stopped without storing any generator state.
 *
 * <p>Stream ids keep subsystems apart: 1-99 world generation, 100-199
 * environment, 200-299 ecosystem, 300-399 civilization, 400-499 host adapters.
 */
public final class DetRandom {
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private DetRandom() {
    }

    /** SplitMix64 finalizer: a fast, well-distributed 64-bit mix. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, long... keys) {
        long h = mix(seed + GOLDEN);
        for (long k : keys) {
            h = mix(h ^ mix(k + GOLDEN));
        }
        return h;
    }

    /** Uniform double in [0, 1). */
    public static double unit(long seed, long... keys) {
        return (hash(seed, keys) >>> 11) * 0x1.0p-53;
    }

    /** Uniform int in [0, bound). */
    public static int below(int bound, long seed, long... keys) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return (int) Math.floorMod(hash(seed, keys), (long) bound);
    }

    /**
     * Rounds a non-negative expected value to a whole number while preserving its
     * expectation: 2.3 becomes 3 with probability 0.3 and 2 otherwise.
     */
    public static int stochasticRound(double expected, long seed, long... keys) {
        if (!(expected > 0)) {
            return 0;
        }
        double floor = Math.floor(expected);
        return (int) floor + (unit(seed, keys) < expected - floor ? 1 : 0);
    }
}
