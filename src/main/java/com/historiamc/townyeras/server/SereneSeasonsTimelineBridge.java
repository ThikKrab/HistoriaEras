package com.historiamc.townyeras.server;

import com.historiamc.townyeras.HistoriaEras;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional, server-owned synchronization between the HistoriaEras calendar and Serene Seasons. */
public final class SereneSeasonsTimelineBridge {
    private static final String MOD_ID = "sereneseasons";
    private static final int SPRING_ANCHOR_DAY = 31 + 28; // March 1 in the fixed HistoriaEras calendar.
    private static final int PACKET_INTERVAL_TICKS = 20;

    private static boolean initializationAttempted;
    private static boolean active;
    private static int packetTicks;
    private static Method getSeasonSavedData;
    private static Method sendSeasonUpdate;
    private static Field seasonCycleTicks;
    private static int cycleDuration;

    private SereneSeasonsTimelineBridge() {
    }

    /**
     * Makes HistoriaEras authoritative each server tick. March 1 begins early
     * spring, June begins summer, September autumn, and December winter.
     */
    public static void sync(MinecraftServer server) {
        if (!initialize()) {
            return;
        }
        try {
            ServerLevel level = server.overworld();
            TimelineState timeline = HistoriaErasSavedData.get(server).timeline();
            int targetTicks = seasonTicks(timeline, cycleDuration);
            Object sereneData = getSeasonSavedData.invoke(null, level);
            int currentTicks = seasonCycleTicks.getInt(sereneData);
            if (currentTicks != targetTicks) {
                seasonCycleTicks.setInt(sereneData, targetTicks);
                ((SavedData) sereneData).setDirty();
            }

            packetTicks++;
            if (packetTicks >= PACKET_INTERVAL_TICKS) {
                packetTicks = 0;
                sendSeasonUpdate.invoke(null, level);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            active = false;
            HistoriaEras.LOGGER.error(
                    "HistoriaEras disabled Serene Seasons timeline synchronization after an API error",
                    exception
            );
        }
    }

    public static boolean active() {
        return active;
    }

    /** Used by the automated compatibility run to verify the external clock, not only bridge discovery. */
    public static boolean synchronizedNow(MinecraftServer server) {
        if (!active) {
            return false;
        }
        try {
            TimelineState timeline = HistoriaErasSavedData.get(server).timeline();
            Object sereneData = getSeasonSavedData.invoke(null, server.overworld());
            return seasonCycleTicks.getInt(sereneData) == seasonTicks(timeline, cycleDuration);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    static int seasonTicks(TimelineState timeline, int sereneCycleDuration) {
        if (sereneCycleDuration <= 0) {
            return 0;
        }
        double calendarDay = timeline.dayOfYear()
                + timeline.ticksTowardNextDay() / (double) timeline.ticksPerCalendarDay();
        double seasonalDay = calendarDay - SPRING_ANCHOR_DAY;
        if (seasonalDay < 0.0D) {
            seasonalDay += TimelineState.CALENDAR_DAYS_PER_YEAR;
        }
        double fraction = seasonalDay / TimelineState.CALENDAR_DAYS_PER_YEAR;
        return Math.clamp((int) Math.floor(fraction * sereneCycleDuration), 0, sereneCycleDuration - 1);
    }

    private static boolean initialize() {
        if (initializationAttempted) {
            return active;
        }
        initializationAttempted = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        try {
            Class<?> handlerClass = Class.forName("sereneseasons.season.SeasonHandler");
            Class<?> timeClass = Class.forName("sereneseasons.season.SeasonTime");
            Class<?> dataClass = Class.forName("sereneseasons.season.SeasonSavedData");
            getSeasonSavedData = handlerClass.getMethod("getSeasonSavedData", Level.class);
            sendSeasonUpdate = handlerClass.getMethod("sendSeasonUpdate", Level.class);
            seasonCycleTicks = dataClass.getField("seasonCycleTicks");
            Object zero = timeClass.getField("ZERO").get(null);
            cycleDuration = (int) timeClass.getMethod("getCycleDuration").invoke(zero);
            if (cycleDuration <= 0) {
                throw new IllegalStateException("Serene Seasons reported a non-positive cycle duration");
            }
            active = true;
            HistoriaEras.LOGGER.info(
                    "HistoriaEras is synchronizing Serene Seasons to its 365-day historical calendar"
            );
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            HistoriaEras.LOGGER.error(
                    "Serene Seasons is installed, but HistoriaEras could not initialize its 1.21.1 integration",
                    exception
            );
            return false;
        }
    }
}
