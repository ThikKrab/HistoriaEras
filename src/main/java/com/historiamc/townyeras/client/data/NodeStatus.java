package com.historiamc.townyeras.client.data;

public enum NodeStatus {
    COMPLETED(0xFF5FA85A, "Completed"),
    AVAILABLE(0xFFD6A94A, "Available"),
    IN_PROGRESS(0xFF4F8CC9, "In progress"),
    LOCKED(0xFF686868, "Locked"),
    CLASS_LOCKED(0xFF8A5A50, "Choose this class"),
    FUTURE_ERA(0xFF4D5964, "Future era"),
    GRANTED(0xFF9168B3, "Granted");

    private final int color;
    private final String label;

    NodeStatus(int color, String label) {
        this.color = color;
        this.label = label;
    }

    public int color() {
        return color;
    }

    public String label() {
        return label;
    }
}
