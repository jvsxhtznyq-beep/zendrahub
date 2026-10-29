package com.zendrahub.evolve.core.persist;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    void parsesAllValueKinds() {
        Object v = Json.parse(" {\"a\": [1, -2.5, 3e2, true, false, null], \"b\": {\"c\": \"x\\\"y\\u00e9\\n\"}} ");
        Map<?, ?> m = (Map<?, ?>) v;
        List<?> a = (List<?>) m.get("a");
        assertEquals(1L, a.get(0));
        assertEquals(-2.5, a.get(1));
        assertEquals(300.0, a.get(2));
        assertEquals(Boolean.TRUE, a.get(3));
        assertEquals(Boolean.FALSE, a.get(4));
        assertNull(a.get(5));
        assertEquals("x\"y\u00e9\n", ((Map<?, ?>) m.get("b")).get("c"));
    }

    @Test
    void doublesRoundTripExactly() {
        double[] values = {0.1, 1.0 / 3.0, 1e-300, 123456789.123456789, -0.0, 4.9e-324, Math.PI};
        for (double d : values) {
            Object back = ((List<?>) Json.parse(Json.write(List.of(d)))).get(0);
            assertEquals(Double.doubleToLongBits(d), Double.doubleToLongBits(((Number) back).doubleValue()));
        }
    }

    @Test
    void writeThenParseIsStable() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "tab\there \u0001");
        m.put("list", List.of(1L, 2.5, "s"));
        m.put("nested", List.of(Map.of("k", 1L)));
        m.put("empty", List.of());
        String once = Json.write(m);
        assertEquals(once, Json.write(Json.parse(once)));
    }

    @Test
    void rejectsMalformedInputWithOffsets() {
        String[] bad = {"", "{", "[1,]", "{\"a\" 1}", "tru", "\"unterminated", "[1] x", "{\"a\":01x}", "\"\\q\""};
        for (String s : bad) {
            Json.JsonException e = assertThrows(Json.JsonException.class, () -> Json.parse(s), s);
            assertTrue(e.getMessage().contains("offset"), e.getMessage());
        }
    }

    @Test
    void refusesNonFiniteNumbers() {
        assertThrows(IllegalArgumentException.class, () -> Json.write(List.of(Double.NaN)));
    }

    @Test
    void deepNestingIsBounded() {
        String deep = "[".repeat(1000) + "]".repeat(1000);
        assertThrows(Json.JsonException.class, () -> Json.parse(deep));
    }
}
