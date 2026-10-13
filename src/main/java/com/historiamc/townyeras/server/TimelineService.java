package com.historiamc.townyeras.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Server-thread facade for the persistent global timeline. */
public final class TimelineService {
    private TimelineService() {
    }

    /** Call once per server tick. */
    public static TimelineAdvance tick(MinecraftServer server) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        TimelineState timeline = data.timeline();
        int beforeDay = timeline.dayOfYear();
        int beforeYear = timeline.historicalYear();
        boolean wasCompleted = timeline.eraCompleted();
        if (!timeline.advanceTicks(1L)) {
            return TimelineAdvance.NONE;
        }
        data.setDirty();
        return new TimelineAdvance(
                beforeDay != timeline.dayOfYear(),
                timeline.historicalYear() - beforeYear,
                !wasCompleted && timeline.eraCompleted()
        );
    }

    /** Primarily useful for deterministic tests and administrator commands. */
    public static TimelineAdvance advanceTicks(MinecraftServer server, long elapsedTicks) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        TimelineState timeline = data.timeline();
        int beforeDay = timeline.dayOfYear();
        int beforeYear = timeline.historicalYear();
        boolean wasCompleted = timeline.eraCompleted();
        if (!timeline.advanceTicks(elapsedTicks)) {
            return TimelineAdvance.NONE;
        }
        data.setDirty();
        return new TimelineAdvance(
                beforeDay != timeline.dayOfYear(),
                timeline.historicalYear() - beforeYear,
                !wasCompleted && timeline.eraCompleted()
        );
    }

    public static TimelineSnapshot snapshot(MinecraftServer server) {
        return TimelineSnapshot.from(HistoriaErasSavedData.get(server).timeline());
    }

    public static CompoundTag createSnapshot(MinecraftServer server) {
        return writeSnapshot(new CompoundTag(), HistoriaErasSavedData.get(server).timeline());
    }

    static CompoundTag writeSnapshot(CompoundTag tag, TimelineState timeline) {
        tag.putString("eraId", timeline.eraId());
        tag.putString("eraName", timeline.eraName());
        tag.putInt("historicalYear", timeline.historicalYear());
        tag.putString("historicalYearDisplay", timeline.historicalYearLabel());
        tag.putInt("dayOfYear", timeline.dayOfYear());
        tag.putString("calendarDate", timeline.calendarDate());
        tag.putString("seasonName", timeline.seasonName());
        tag.putBoolean("sereneSeasonsSynced", SereneSeasonsTimelineBridge.active());
        tag.putDouble("eraProgress", timeline.eraProgress());
        tag.putBoolean("paused", timeline.paused());
        tag.putBoolean("eraCompleted", timeline.eraCompleted());
        tag.putLong("ticksPerCalendarDay", timeline.ticksPerCalendarDay());
        return tag;
    }

    public static void setPaused(MinecraftServer server, boolean paused) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        data.timeline().setPaused(paused);
        data.setDirty();
    }

    public static void resetAncient(MinecraftServer server) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        data.timeline().resetAncient();
        data.setDirty();
    }

    /** Explicitly starts a supported era at its January 1 boundary. */
    public static boolean startEra(MinecraftServer server, String eraId) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        if (!data.timeline().startEra(eraId)) {
            return false;
        }
        data.setDirty();
        return true;
    }

    /** Advances a completed Ancient era to Classical, preserving the manual transition gate. */
    public static boolean advanceEra(MinecraftServer server) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        if (!data.timeline().advanceEra()) {
            return false;
        }
        data.setDirty();
        return true;
    }

    public static boolean setDate(MinecraftServer server, int historicalYear, int dayOfYear) {
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        if (!data.timeline().setDate(historicalYear, dayOfYear)) {
            return false;
        }
        data.setDirty();
        return true;
    }

    public record TimelineAdvance(boolean calendarDayChanged, int historicalYearsAdvanced, boolean eraEnded) {
        public static final TimelineAdvance NONE = new TimelineAdvance(false, 0, false);

        public boolean changed() {
            return calendarDayChanged || historicalYearsAdvanced != 0 || eraEnded;
        }
    }

    public record TimelineSnapshot(
            String eraId,
            String eraName,
            int historicalYear,
            int dayOfYear,
            String calendarDate,
            double eraProgress,
            boolean paused,
            boolean eraCompleted
    ) {
        static TimelineSnapshot from(TimelineState timeline) {
            return new TimelineSnapshot(
                    timeline.eraId(),
                    timeline.eraName(),
                    timeline.historicalYear(),
                    timeline.dayOfYear(),
                    timeline.calendarDate(),
                    timeline.eraProgress(),
                    timeline.paused(),
                    timeline.eraCompleted()
            );
        }
    }
}
