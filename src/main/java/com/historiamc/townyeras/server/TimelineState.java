package com.historiamc.townyeras.server;

import net.minecraft.nbt.CompoundTag;

import java.util.Locale;

/**
 * Persistent fixed-365-day Ancient-era calendar.
 *
 * <p>A visible calendar year always runs January 1 through December 31. At the
 * transition back to January 1 the historical year advances by exactly 100 years.
 * At 144 calendar days per real day, one calendar day takes ten real minutes and
 * the full Ancient era takes about 88.7 real days of server uptime.</p>
 */
public final class TimelineState {
    public static final String ANCIENT_ERA_ID = "ancient";
    public static final String ANCIENT_ERA_NAME = "Ancient Era";
    public static final String CLASSICAL_ERA_ID = "classical";
    public static final String CLASSICAL_ERA_NAME = "Classical Era";
    public static final int ANCIENT_START_YEAR = -4000;
    public static final int ANCIENT_END_YEAR = -500;
    public static final int CLASSICAL_START_YEAR = -500;
    public static final int CLASSICAL_END_YEAR = 500;
    public static final int HISTORICAL_YEARS_PER_CALENDAR_YEAR = 100;
    public static final int CALENDAR_DAYS_PER_YEAR = 365;
    public static final int CALENDAR_DAYS_PER_REAL_DAY = 144;
    public static final int CLASSICAL_CALENDAR_DAYS_PER_REAL_DAY = 72;
    public static final long TICKS_PER_REAL_DAY = 20L * 60L * 60L * 24L;
    public static final long TICKS_PER_CALENDAR_DAY = TICKS_PER_REAL_DAY / CALENDAR_DAYS_PER_REAL_DAY;

    private static final int[] MONTH_LENGTHS = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    private String eraId = ANCIENT_ERA_ID;
    private int historicalYear = ANCIENT_START_YEAR;
    private int dayOfYear;
    private long ticksTowardNextDay;
    private boolean paused;
    private boolean eraCompleted;

    /** Deterministic startup guard for the date rules that previously caused skipped years. */
    public static void validateModel() {
        TimelineState dayBoundary = new TimelineState();
        require(dayBoundary.advanceTicks(TICKS_PER_CALENDAR_DAY - 1L), "partial day was not recorded");
        require(dayBoundary.dayOfYear == 0 && dayBoundary.historicalYear == ANCIENT_START_YEAR,
                "historical time changed inside January 1");
        require(dayBoundary.advanceTicks(1L), "calendar-day boundary was not recorded");
        require(dayBoundary.dayOfYear == 1 && dayBoundary.historicalYear == ANCIENT_START_YEAR,
                "ordinary calendar day changed the historical year");

        TimelineState yearBoundary = new TimelineState();
        yearBoundary.advanceTicks(TICKS_PER_CALENDAR_DAY * 364L);
        require(yearBoundary.dayOfYear == 364 && yearBoundary.historicalYear == ANCIENT_START_YEAR,
                "December 31 boundary is incorrect");
        yearBoundary.advanceTicks(TICKS_PER_CALENDAR_DAY);
        require(yearBoundary.dayOfYear == 0
                        && yearBoundary.historicalYear == ANCIENT_START_YEAR + HISTORICAL_YEARS_PER_CALENDAR_YEAR,
                "January 1 did not advance exactly 100 historical years");

        TimelineState ancient = new TimelineState();
        int ancientCycles = (ANCIENT_END_YEAR - ANCIENT_START_YEAR) / HISTORICAL_YEARS_PER_CALENDAR_YEAR;
        ancient.advanceTicks(TICKS_PER_CALENDAR_DAY * CALENDAR_DAYS_PER_YEAR * ancientCycles);
        require(ancient.historicalYear == ANCIENT_END_YEAR && ancient.dayOfYear == 0
                        && ancient.eraCompleted && ancient.paused,
                "Ancient era did not freeze at its exact January 1 boundary");
        require(ancient.advanceEra() && ancient.eraId.equals(CLASSICAL_ERA_ID)
                        && ancient.historicalYear == CLASSICAL_START_YEAR && !ancient.paused,
                "manual Ancient-to-Classical transition failed");
        ancient.advanceTicks(ancient.ticksPerCalendarDay() * CALENDAR_DAYS_PER_YEAR);
        require(ancient.historicalYear == CLASSICAL_START_YEAR + HISTORICAL_YEARS_PER_CALENDAR_YEAR,
                "Classical calendar did not preserve the 100-year rollover rule");

        TimelineState seasons = new TimelineState();
        require(seasons.seasonName().equals("Mid Winter"), "January did not map to mid winter");
        seasons.setDate(ANCIENT_START_YEAR, 31 + 28);
        require(seasons.seasonName().equals("Early Spring"), "March 1 did not begin spring");
        seasons.setDate(ANCIENT_START_YEAR, 31 + 28 + 31 + 30 + 31);
        require(seasons.seasonName().equals("Early Summer"), "June 1 did not begin summer");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("HistoriaEras timeline self-test failed: " + message);
        }
    }

    static TimelineState load(CompoundTag tag) {
        TimelineState state = new TimelineState();
        String savedEra = tag.getString("eraId");
        state.eraId = isSupportedEra(savedEra) ? savedEra : ANCIENT_ERA_ID;
        int savedYear = tag.contains("historicalYear") ? tag.getInt("historicalYear") : state.eraStartYear();
        state.historicalYear = state.clampHistoricalYear(savedYear);
        state.dayOfYear = Math.clamp(tag.getInt("dayOfYear"), 0, CALENDAR_DAYS_PER_YEAR - 1);
        state.ticksTowardNextDay = Math.clamp(
                tag.getLong("ticksTowardNextDay"),
                0L,
                state.ticksPerCalendarDay() - 1L
        );
        state.eraCompleted = tag.getBoolean("eraCompleted") || state.historicalYear >= state.eraEndYear();
        state.paused = tag.getBoolean("paused") || state.eraCompleted;
        if (state.eraCompleted) {
            state.historicalYear = state.eraEndYear();
            state.dayOfYear = 0;
            state.ticksTowardNextDay = 0L;
        }
        return state;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("eraId", eraId);
        tag.putInt("historicalYear", historicalYear);
        tag.putInt("dayOfYear", dayOfYear);
        tag.putLong("ticksTowardNextDay", ticksTowardNextDay);
        tag.putBoolean("paused", paused);
        tag.putBoolean("eraCompleted", eraCompleted);
        return tag;
    }

    /** Returns true when any calendar state changed. */
    boolean advanceTicks(long elapsedTicks) {
        if (elapsedTicks <= 0L || paused || eraCompleted) {
            return false;
        }
        ticksTowardNextDay += elapsedTicks;
        long ticksPerDay = ticksPerCalendarDay();
        while (ticksTowardNextDay >= ticksPerDay && !eraCompleted) {
            ticksTowardNextDay -= ticksPerDay;
            advanceOneCalendarDay();
        }
        return true;
    }

    private void advanceOneCalendarDay() {
        if (dayOfYear < CALENDAR_DAYS_PER_YEAR - 1) {
            dayOfYear++;
            return;
        }

        // The only place the displayed historical year changes. December 31 is
        // followed by January 1 exactly once, and that transition adds 100 years.
        dayOfYear = 0;
        historicalYear = Math.min(eraEndYear(), historicalYear + HISTORICAL_YEARS_PER_CALENDAR_YEAR);
        if (historicalYear >= eraEndYear()) {
            historicalYear = eraEndYear();
            ticksTowardNextDay = 0L;
            eraCompleted = true;
            paused = true;
        }
    }

    void resetAncient() {
        eraId = ANCIENT_ERA_ID;
        historicalYear = ANCIENT_START_YEAR;
        dayOfYear = 0;
        ticksTowardNextDay = 0L;
        paused = false;
        eraCompleted = false;
    }

    boolean setDate(int historicalYear, int dayOfYear) {
        if (historicalYear < eraStartYear() || historicalYear > eraEndYear()
                || Math.floorMod(historicalYear - eraStartYear(), HISTORICAL_YEARS_PER_CALENDAR_YEAR) != 0
                || dayOfYear < 0 || dayOfYear >= CALENDAR_DAYS_PER_YEAR) {
            return false;
        }
        this.historicalYear = historicalYear;
        this.dayOfYear = dayOfYear;
        this.ticksTowardNextDay = 0L;
        this.eraCompleted = historicalYear == eraEndYear();
        this.paused = this.eraCompleted;
        return true;
    }

    boolean startEra(String requestedEraId) {
        if (!isSupportedEra(requestedEraId)) {
            return false;
        }
        eraId = requestedEraId;
        historicalYear = eraStartYear();
        dayOfYear = 0;
        ticksTowardNextDay = 0L;
        paused = false;
        eraCompleted = false;
        return true;
    }

    boolean advanceEra() {
        if (!eraCompleted || !eraId.equals(ANCIENT_ERA_ID)) {
            return false;
        }
        return startEra(CLASSICAL_ERA_ID);
    }

    void setPaused(boolean paused) {
        this.paused = eraCompleted || paused;
    }

    public String eraId() {
        return eraId;
    }

    public String eraName() {
        return eraId.equals(CLASSICAL_ERA_ID) ? CLASSICAL_ERA_NAME : ANCIENT_ERA_NAME;
    }

    public int historicalYear() {
        return historicalYear;
    }

    public int dayOfYear() {
        return dayOfYear;
    }

    public long ticksTowardNextDay() {
        return ticksTowardNextDay;
    }

    public boolean paused() {
        return paused;
    }

    public boolean eraCompleted() {
        return eraCompleted;
    }

    public int month() {
        int remaining = dayOfYear;
        for (int month = 0; month < MONTH_LENGTHS.length; month++) {
            if (remaining < MONTH_LENGTHS[month]) {
                return month + 1;
            }
            remaining -= MONTH_LENGTHS[month];
        }
        return 12;
    }

    public int dayOfMonth() {
        int remaining = dayOfYear;
        for (int length : MONTH_LENGTHS) {
            if (remaining < length) {
                return remaining + 1;
            }
            remaining -= length;
        }
        return 31;
    }

    public String calendarDate() {
        return MONTH_NAMES[month() - 1] + " " + dayOfMonth();
    }

    public String historicalYearLabel() {
        if (historicalYear < 0) {
            return String.format(Locale.ROOT, "%d BCE", Math.abs(historicalYear));
        }
        if (historicalYear > 0) {
            return String.format(Locale.ROOT, "%d CE", historicalYear);
        }
        return "Year 0";
    }

    /** Northern-hemisphere season aligned to the fixed calendar months. */
    public String seasonName() {
        int seasonalDay = Math.floorMod(dayOfYear - (31 + 28), CALENDAR_DAYS_PER_YEAR);
        int subSeason = Math.min(11, seasonalDay * 12 / CALENDAR_DAYS_PER_YEAR);
        String phase = switch (subSeason % 3) {
            case 0 -> "Early";
            case 1 -> "Mid";
            default -> "Late";
        };
        String season = switch (subSeason / 3) {
            case 0 -> "Spring";
            case 1 -> "Summer";
            case 2 -> "Autumn";
            default -> "Winter";
        };
        return phase + " " + season;
    }

    /** Continuous 0..1 position used by the in-game era bar. */
    public double eraProgress() {
        if (eraCompleted) {
            return 1.0D;
        }
        int completedCalendarYears = (historicalYear - eraStartYear()) / HISTORICAL_YEARS_PER_CALENDAR_YEAR;
        double elapsedCalendarDays = completedCalendarYears * (double) CALENDAR_DAYS_PER_YEAR
                + dayOfYear
                + ticksTowardNextDay / (double) ticksPerCalendarDay();
        int totalCalendarYears = (eraEndYear() - eraStartYear()) / HISTORICAL_YEARS_PER_CALENDAR_YEAR;
        return Math.clamp(elapsedCalendarDays / (totalCalendarYears * CALENDAR_DAYS_PER_YEAR), 0.0D, 1.0D);
    }

    public int eraStartYear() {
        return eraId.equals(CLASSICAL_ERA_ID) ? CLASSICAL_START_YEAR : ANCIENT_START_YEAR;
    }

    public int eraEndYear() {
        return eraId.equals(CLASSICAL_ERA_ID) ? CLASSICAL_END_YEAR : ANCIENT_END_YEAR;
    }

    public long ticksPerCalendarDay() {
        int daysPerRealDay = eraId.equals(CLASSICAL_ERA_ID)
                ? CLASSICAL_CALENDAR_DAYS_PER_REAL_DAY
                : CALENDAR_DAYS_PER_REAL_DAY;
        return TICKS_PER_REAL_DAY / daysPerRealDay;
    }

    private int clampHistoricalYear(int year) {
        int clamped = Math.clamp(year, eraStartYear(), eraEndYear());
        int cycles = Math.floorDiv(clamped - eraStartYear(), HISTORICAL_YEARS_PER_CALENDAR_YEAR);
        return eraStartYear() + cycles * HISTORICAL_YEARS_PER_CALENDAR_YEAR;
    }

    private static boolean isSupportedEra(String id) {
        return ANCIENT_ERA_ID.equals(id) || CLASSICAL_ERA_ID.equals(id);
    }
}
