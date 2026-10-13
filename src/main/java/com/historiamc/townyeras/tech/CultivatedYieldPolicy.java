package com.historiamc.townyeras.tech;

/** Five-percent chance of doubling edible cultivated drops; seeds remain unchanged. */
public final class CultivatedYieldPolicy {
    private CultivatedYieldPolicy() {}
    public static boolean bonus(boolean unlocked, float roll) {
        return unlocked && roll >= 0 && roll < 0.05F;
    }
    public static boolean eligible(TechRegistry tree, String item) {
        // The generic seed category intentionally includes edible planting items such
        // as carrots and potatoes. They are crop produce, not separate seed drops.
        String bare = item.substring(item.indexOf(':') + 1);
        return tree.matches(item, "#reviewed_crop_items") && !bare.contains("seed");
    }
}
