package com.historiamc.townyeras.client;

import com.historiamc.townyeras.HistoriaEras;
import com.historiamc.townyeras.client.screen.HistoriaErasScreen;
import com.historiamc.townyeras.network.ClientSnapshotCache;
import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechnologyManager;
import com.historiamc.townyeras.tech.TechRequirement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.Locale;

/** Always-visible historical clock and era progress bar. */
public final class HistoriaErasHud {
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(
            HistoriaEras.MOD_ID,
            "timeline"
    );

    private HistoriaErasHud() {
    }

    public static void register(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, HistoriaErasHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.screen instanceof HistoriaErasScreen
                || !ClientSnapshotCache.isSynchronizedWithServer()) {
            return;
        }

        CompoundTag snapshot = ClientSnapshotCache.latest();
        CompoundTag timeline = snapshot.getCompound("timeline");
        if (timeline.isEmpty()) {
            return;
        }

        int trackedObjectiveY = 4;
        if (HistoriaErasClientPreferences.calendarBarVisible()) {
            String era = timeline.getString("eraName");
            String historicalYear = timeline.getString("historicalYearDisplay");
            if (historicalYear.isBlank()) {
                int year = timeline.getInt("historicalYear");
                historicalYear = year < 0 ? Math.abs(year) + " BCE" : year > 0 ? year + " CE" : "Year 0";
            }
            String date = timeline.getString("calendarDate");
            String label = era + "  •  " + historicalYear + "  •  " + date;

            int width = Math.min(292, Math.max(176, minecraft.font.width(label) + 20));
            int x = (graphics.guiWidth() - width) / 2;
            int y = 4;
            int height = 17;
            double progress = Math.max(0.0D, Math.min(1.0D, timeline.getDouble("eraProgress")));
            int innerWidth = width - 4;
            int filled = (int) Math.round(innerWidth * progress);

            graphics.fill(x, y, x + width, y + height, 0xD9101112);
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xE52C2D2E);
            if (filled > 0) {
                graphics.fill(x + 2, y + height - 4, x + 2 + filled, y + height - 2,
                        eraColor(timeline.getString("eraId")));
            }
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF707275);
            graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF111213);
            graphics.drawCenteredString(minecraft.font, label, graphics.guiWidth() / 2, y + 4, 0xFFF2E8C4);
            trackedObjectiveY = y + height + 3;
        }
        renderTrackedObjective(graphics, minecraft, snapshot, trackedObjectiveY);
    }

    private static void renderTrackedObjective(
            GuiGraphics graphics,
            Minecraft minecraft,
            CompoundTag snapshot,
            int y
    ) {
        String trackedId = snapshot.getString("trackedNode");
        TechNode node = TechnologyManager.current().node(trackedId);
        if (node == null) {
            return;
        }

        CompoundTag contributions = snapshot.getCompound("contributions");
        TechRequirement next = null;
        double currentTotal = 0.0D;
        double requiredTotal = 0.0D;
        double nextCurrent = 0.0D;
        for (TechRequirement requirement : node.requirements()) {
            double current = Math.clamp(
                    contributions.getDouble(node.id() + "|" + requirement.id()),
                    0.0D,
                    requirement.amount()
            );
            currentTotal += current;
            requiredTotal += requirement.amount();
            if (next == null && current < requirement.amount()) {
                next = requirement;
                nextCurrent = current;
            }
        }

        boolean completed = snapshot.getCompound("nodeStatuses").getString(node.id()).equals("completed");
        double progress = completed || requiredTotal <= 0.0D
                ? 1.0D
                : Math.clamp(currentTotal / requiredTotal, 0.0D, 1.0D);
        String objective = next == null
                ? "Complete"
                : next.display() + " " + format(nextCurrent) + "/" + format(next.amount());
        String fullLabel = "Tracking: " + node.displayName() + "  •  " + objective;
        int width = Math.min(340, Math.max(176, minecraft.font.width(fullLabel) + 20));
        String label = minecraft.font.plainSubstrByWidth(fullLabel, width - 12);
        int x = (graphics.guiWidth() - width) / 2;
        int height = 17;
        int filled = (int) Math.round((width - 4) * progress);

        graphics.fill(x, y, x + width, y + height, 0xD9101112);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xE52C2D2E);
        if (filled > 0) {
            graphics.fill(
                    x + 2,
                    y + height - 4,
                    x + 2 + filled,
                    y + height - 2,
                    completed ? 0xFF65AE6A : 0xFFD8A83C
            );
        }
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0xFF707275);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, 0xFF111213);
        graphics.drawCenteredString(minecraft.font, label, graphics.guiWidth() / 2, y + 4, 0xFFF2EEE4);
    }

    private static String format(double value) {
        long rounded = Math.round(value);
        return Math.abs(value - rounded) < 0.000_001D
                ? Long.toString(rounded)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static int eraColor(String eraId) {
        return switch (eraId) {
            case "classical" -> 0xFFD48ABF;
            case "medieval" -> 0xFF65AE6A;
            case "renaissance" -> 0xFFDCA84B;
            case "industrial" -> 0xFFB6B8BB;
            case "modern", "information" -> 0xFF6296D6;
            case "atomic", "future" -> 0xFF9A72D5;
            default -> 0xFFD8A83C;
        };
    }
}
