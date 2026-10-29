package com.zendrahub.evolve.core.persist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader and writer for save files, so the core needs no
 * libraries. Objects read as {@code LinkedHashMap<String, Object>}, arrays as
 * {@code ArrayList<Object>}, integers as {@code Long}, other numbers as
 * {@code Double}. Doubles are written with {@link Double#toString(double)},
 * which round-trips every finite value exactly on Java 19 and later.
 */
public final class Json {
    private static final int MAX_DEPTH = 256;

    private Json() {
    }

    /** Thrown for malformed input; the message carries the character offset. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (p.pos != text.length()) {
            throw p.error("unexpected trailing content");
        }
        return value;
    }

    /** Pretty-printed JSON; arrays of plain values stay on one line. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder(4096);
        writeValue(sb, value, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("cannot write non-finite number " + d);
            }
            sb.append(Double.toString(d));
        } else if (v instanceof Number n) {
            sb.append(n.longValue());
        } else if (v instanceof Map<?, ?> m) {
            writeObject(sb, m, indent);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l, indent);
        } else {
            throw new IllegalArgumentException("cannot write " + v.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> m, int indent) {
        if (m.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            indent(sb, indent + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(": ");
            writeValue(sb, e.getValue(), indent + 1);
            if (++i < m.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> l, int indent) {
        if (l.isEmpty()) {
            sb.append("[]");
            return;
        }
        boolean flat = true;
        for (Object o : l) {
            if (o instanceof Map || o instanceof List) {
                flat = false;
                break;
            }
        }
        if (flat) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                writeValue(sb, l.get(i), indent);
            }
            sb.append(']');
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < l.size(); i++) {
            indent(sb, indent + 1);
            writeValue(sb, l.get(i), indent + 1);
            if (i < l.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        JsonException error(String what) {
            return new JsonException(what + " at offset " + pos);
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("nesting too deep");
            }
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> readObject(depth);
                case '[' -> readArray(depth);
                case '"' -> readString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield readNumber();
                    }
                    throw error("unexpected character '" + c + "'");
                }
            };
        }

        Object literal(String word, Object value) {
            if (!s.startsWith(word, pos)) {
                throw error("expected " + word);
            }
            pos += word.length();
            return value;
        }

        Map<String, Object> readObject(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected object key");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                m.put(key, readValue(depth + 1));
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or '}'");
                }
            }
        }

        List<Object> readArray(int depth) {
            List<Object> l = new ArrayList<>();
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return l;
            }
            while (true) {
                skipWhitespace();
                l.add(readValue(depth + 1));
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return l;
                }
                if (c != ',') {
                    pos--;
                    throw error("expected ',' or ']'");
                }
            }
        }

        String readString() {
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= s.length()) {
                    throw error("unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= s.length()) {
                    throw error("unterminated escape");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) {
                            throw error("bad unicode escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("bad unicode escape");
                        }
                        pos += 4;
                    }
                    default -> throw error("bad escape '\\" + e + "'");
                }
            }
        }

        Object readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            boolean fraction = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    fraction = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            try {
                if (!fraction) {
                    try {
                        return Long.parseLong(num);
                    } catch (NumberFormatException overflow) {
                        return Double.parseDouble(num);
                    }
                }
                return Double.parseDouble(num);
            } catch (NumberFormatException ex) {
                pos = start;
                throw error("bad number '" + num + "'");
            }
        }

        char peek() {
            if (pos >= s.length()) {
                throw error("unexpected end of input");
            }
            return s.charAt(pos);
        }

        char next() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            if (next() != c) {
                pos--;
                throw error("expected '" + c + "'");
            }
        }
    }
}
