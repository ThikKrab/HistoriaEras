package com.historiamc.townyeras.client.data;

import com.historiamc.townyeras.tech.TechEra;
import java.util.Map;

/** Browsing permissions use chronology, not the server's research-lock status. */
public final class TechnologyEraVisibility {
    private TechnologyEraVisibility() {}

    public static boolean allowed(boolean adminMode, String currentId, String requestedId,
                                  Map<String, TechEra> eras) {
        TechEra requested = eras.get(requestedId);
        TechEra current = eras.get(currentId);
        if (requested == null) return false;
        if (adminMode) return true;
        return current != null && !requested.startDate().toLocalDate().isAfter(current.startDate().toLocalDate());
    }
}
