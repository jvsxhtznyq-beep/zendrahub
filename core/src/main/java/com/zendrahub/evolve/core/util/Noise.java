package com.zendrahub.evolve.core.util;

/** Seeded 2D value noise for world generation. Deterministic for a given seed and salt. */
public final class Noise {
    private Noise() {
    }

    /** Smooth noise in [0, 1) sampled on a lattice of the given cell size. */
    public static double value(long seed, long salt, double x, double z, double scale) {
        double fx = x / scale;
        double fz = z / scale;
        long x0 = (long) Math.floor(fx);
        long z0 = (long) Math.floor(fz);
        double tx = smooth(fx - x0);
        double tz = smooth(fz - z0);
        double a = DetRandom.unit(seed, salt, x0, z0);
        double b = DetRandom.unit(seed, salt, x0 + 1, z0);
        double c = DetRandom.unit(seed, salt, x0, z0 + 1);
        double d = DetRandom.unit(seed, salt, x0 + 1, z0 + 1);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    /** Two octaves of value noise, normalised back to [0, 1). */
    public static double fractal(long seed, long salt, double x, double z, double scale) {
        double n = value(seed, salt, x, z, scale) * 0.65
                + value(seed, salt + 7919, x, z, scale / 2.0) * 0.35;
        return Math.min(Math.max(n, 0.0), 0.999999);
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
