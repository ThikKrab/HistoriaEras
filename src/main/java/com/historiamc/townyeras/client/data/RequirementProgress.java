package com.historiamc.townyeras.client.data;

public record RequirementProgress(
        String id,
        String display,
        String action,
        double current,
        double required
) {
    public double fraction() {
        if (required <= 0.0D) {
            return 1.0D;
        }
        return Math.clamp(current / required, 0.0D, 1.0D);
    }

    public boolean complete() {
        return current + 0.0001D >= required;
    }

    public String formattedProgress() {
        return format(current) + "/" + format(required);
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.0001D) {
            return Long.toString(rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
