package com.historiamc.townyeras.client.data;

import java.util.Collection;

/** Determines which class branches the Technology page may expose. */
public final class TechnologyBranchVisibility {
    private TechnologyBranchVisibility() {
    }

    public static boolean allowed(boolean adminMode, Collection<String> selectedClasses, String classId) {
        if (classId == null) {
            return false;
        }
        return adminMode || classId.equals("universal") || selectedClasses.contains(classId);
    }
}
