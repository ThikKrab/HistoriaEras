package com.historiamc.townyeras.client.data;

/** Immutable client view of the server-owned historical calendar. */
public record TimelineView(
        String eraId,
        String eraName,
        int historicalYear,
        String historicalYearDisplay,
        int dayOfYear,
        String calendarDate,
        String seasonName,
        boolean sereneSeasonsSynced,
        double eraProgress,
        boolean paused,
        boolean eraCompleted,
        long ticksPerCalendarDay
) {
    public TimelineView {
        eraId = eraId == null ? "ancient" : eraId;
        eraName = eraName == null ? "Ancient Era" : eraName;
        historicalYearDisplay = historicalYearDisplay == null ? "4000 BCE" : historicalYearDisplay;
        calendarDate = calendarDate == null ? "January 1" : calendarDate;
        seasonName = seasonName == null ? "Mid Winter" : seasonName;
        eraProgress = Math.clamp(eraProgress, 0.0D, 1.0D);
        dayOfYear = Math.clamp(dayOfYear, 0, 364);
        ticksPerCalendarDay = Math.max(1L, ticksPerCalendarDay);
    }

    public static TimelineView demonstration() {
        return new TimelineView(
                "ancient",
                "Ancient Era",
                -3100,
                "3100 BCE",
                0,
                "January 1",
                "Mid Winter",
                false,
                0.257D,
                false,
                false,
                12_000L
        );
    }
}
