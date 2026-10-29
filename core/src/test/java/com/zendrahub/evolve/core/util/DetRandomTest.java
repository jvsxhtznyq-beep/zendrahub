package com.zendrahub.evolve.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetRandomTest {

    @Test
    void sameKeysGiveSameValue() {
        assertEquals(DetRandom.unit(42, 1, 2, 3), DetRandom.unit(42, 1, 2, 3));
        assertNotEquals(DetRandom.unit(42, 1, 2, 3), DetRandom.unit(42, 1, 2, 4));
        assertNotEquals(DetRandom.unit(42, 1, 2, 3), DetRandom.unit(43, 1, 2, 3));
    }

    @Test
    void unitIsUniformEnough() {
        int n = 100_000;
        int[] buckets = new int[10];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            double u = DetRandom.unit(7, i);
            assertTrue(u >= 0 && u < 1);
            buckets[(int) (u * 10)]++;
            sum += u;
        }
        assertEquals(0.5, sum / n, 0.01);
        for (int b : buckets) {
            assertEquals(n / 10.0, b, n * 0.01);
        }
    }

    @Test
    void stochasticRoundKeepsExpectation() {
        double total = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            int r = DetRandom.stochasticRound(2.3, 99, i);
            assertTrue(r == 2 || r == 3);
            total += r;
        }
        assertEquals(2.3, total / n, 0.02);
        assertEquals(0, DetRandom.stochasticRound(-1, 1, 1));
        assertEquals(0, DetRandom.stochasticRound(Double.NaN, 1, 1));
    }

    @Test
    void belowStaysInRange() {
        for (int i = 0; i < 1000; i++) {
            int v = DetRandom.below(7, 3, i);
            assertTrue(v >= 0 && v < 7);
        }
    }
}
