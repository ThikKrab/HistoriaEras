package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** Explicit functional station that may be protected by an interaction gate. */
public record TechStation(
        String id,
        String className,
        String nodeId,
        String nodeName,
        String gateId,
        boolean inventoryProtected,
        List<String> targets
) {
    public TechStation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(gateId, "gateId");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
