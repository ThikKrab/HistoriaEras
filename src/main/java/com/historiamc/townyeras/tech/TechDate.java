package com.historiamc.townyeras.tech;

import java.time.LocalDate;

/** A proleptic historical date; negative years use astronomical numbering. */
public record TechDate(int year, int month, int day) {
    public LocalDate toLocalDate() {
        return LocalDate.of(year, month, day);
    }
}
