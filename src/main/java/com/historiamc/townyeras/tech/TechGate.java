package com.historiamc.townyeras.tech;

import java.util.List;
import java.util.Objects;

/** Immutable access gate for one operation type and one or more target matchers. */
public record TechGate(
        String id,
        String type,
        String className,
        String nodeId,
        String nodeName,
        boolean messageClassOnly,
        List<String> targets
) {
    public TechGate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(nodeName, "nodeName");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
    }
}
