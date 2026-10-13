package com.historiamc.townyeras.tech;

import java.util.Map;

/** Research can catch up, but cannot jump ahead of the server calendar. */
public final class ResearchEraPolicy {
    private ResearchEraPolicy() {}

    public static boolean available(Map<String, TechEra> eras, String currentId, String requestedId) {
        TechEra current = eras.get(currentId);
        TechEra requested = eras.get(requestedId);
        return current != null && requested != null
                && !requested.startDate().toLocalDate().isAfter(current.startDate().toLocalDate());
    }
}
