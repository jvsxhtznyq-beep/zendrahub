package com.zendrahub.evolve.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Compact chat formatting for command output. */
final class Text {
    private Text() {
    }

    static MutableComponent header(String title) {
        return Component.literal("== " + title + " ==").withStyle(ChatFormatting.GOLD);
    }

    /** "label value" with the label dimmed. */
    static MutableComponent field(String label, String value) {
        return Component.literal(label + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(value).withStyle(ChatFormatting.WHITE));
    }

    static MutableComponent line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(color);
    }

    static MutableComponent bullet(String text) {
        return Component.literal("  - " + text).withStyle(ChatFormatting.GRAY);
    }

    /** Whole numbers for large values, one decimal for small ones. */
    static String num(double v) {
        return Math.abs(v) >= 100 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.1f", v);
    }

    static String two(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String pct(double fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100);
    }

    static String pct1(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    static String lower(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
