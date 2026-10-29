package com.zendrahub.evolve.core.persist;

import java.util.List;
import java.util.Map;

/** Typed, validated access to one decoded JSON object; errors name the offending path. */
final class Fields {
    private final Map<String, Object> map;
    private final String path;

    Fields(Object value, String path) {
        if (!(value instanceof Map<?, ?>)) {
            throw new SaveFormatException(path + ": expected an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) value;
        this.map = m;
        this.path = path;
    }

    boolean has(String key) {
        return map.containsKey(key) && map.get(key) != null;
    }

    private Object require(String key) {
        Object v = map.get(key);
        if (v == null) {
            throw new SaveFormatException(path + "." + key + ": missing");
        }
        return v;
    }

    long getLong(String key) {
        Object v = require(key);
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 9.0e15) {
            return d.longValue();
        }
        throw new SaveFormatException(path + "." + key + ": expected an integer");
    }

    int getInt(String key) {
        long v = getLong(key);
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            throw new SaveFormatException(path + "." + key + ": out of range");
        }
        return (int) v;
    }

    int getInt(String key, int fallback) {
        return has(key) ? getInt(key) : fallback;
    }

    double getDouble(String key) {
        Object v = require(key);
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (!Double.isFinite(d)) {
                throw new SaveFormatException(path + "." + key + ": not finite");
            }
            return d;
        }
        throw new SaveFormatException(path + "." + key + ": expected a number");
    }

    double getDouble(String key, double fallback) {
        return has(key) ? getDouble(key) : fallback;
    }

    boolean getBool(String key) {
        Object v = require(key);
        if (v instanceof Boolean b) {
            return b;
        }
        throw new SaveFormatException(path + "." + key + ": expected true or false");
    }

    boolean getBool(String key, boolean fallback) {
        return has(key) ? getBool(key) : fallback;
    }

    String getString(String key) {
        Object v = require(key);
        if (v instanceof String s) {
            return s;
        }
        throw new SaveFormatException(path + "." + key + ": expected a string");
    }

    String getString(String key, String fallback) {
        return has(key) ? getString(key) : fallback;
    }

    <E extends Enum<E>> E getEnum(String key, Class<E> type) {
        String name = getString(key);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new SaveFormatException(path + "." + key + ": unknown " + type.getSimpleName() + " '" + name + "'");
        }
    }

    List<Object> getList(String key) {
        Object v = require(key);
        if (v instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) v;
            return l;
        }
        throw new SaveFormatException(path + "." + key + ": expected an array");
    }

    List<Object> getList(String key, int expectedSize) {
        List<Object> l = getList(key);
        if (l.size() != expectedSize) {
            throw new SaveFormatException(path + "." + key + ": expected " + expectedSize + " entries, found " + l.size());
        }
        return l;
    }

    Fields getObject(String key) {
        return new Fields(require(key), path + "." + key);
    }

    Map<String, Object> raw() {
        return map;
    }

    String path() {
        return path;
    }

    static double asDouble(Object v, String where) {
        if (v instanceof Number n && Double.isFinite(n.doubleValue())) {
            return n.doubleValue();
        }
        throw new SaveFormatException(where + ": expected a finite number");
    }

    static int asInt(Object v, String where) {
        if (v instanceof Long l && l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
            return (int) (long) l;
        }
        throw new SaveFormatException(where + ": expected an integer");
    }
}
