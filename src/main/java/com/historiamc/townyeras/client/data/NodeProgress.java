package com.historiamc.townyeras.client.data;

import java.util.List;

public record NodeProgress(
        NodeStatus status,
        int currentAmount,
        int requiredAmount,
        List<RequirementProgress> requirements,
        String reason,
        boolean accessible
) {
    public NodeProgress {
        requirements = List.copyOf(requirements);
        reason = reason == null ? "" : reason;
    }

    public NodeProgress(NodeStatus status, int currentAmount, int requiredAmount) {
        this(status, currentAmount, requiredAmount, List.of(), "", status == NodeStatus.COMPLETED || status == NodeStatus.GRANTED);
    }

    public NodeProgress(NodeStatus status, List<RequirementProgress> requirements, String reason) {
        this(status, requirements, reason, status == NodeStatus.COMPLETED || status == NodeStatus.GRANTED);
    }

    public NodeProgress(NodeStatus status, List<RequirementProgress> requirements, String reason, boolean accessible) {
        this(
                status,
                (int) Math.round(requirements.stream().mapToDouble(RequirementProgress::current).sum()),
                (int) Math.round(requirements.stream().mapToDouble(RequirementProgress::required).sum()),
                requirements,
                reason,
                accessible
        );
    }

    public float fraction() {
        if (requiredAmount <= 0) {
            return status == NodeStatus.COMPLETED || status == NodeStatus.GRANTED ? 1.0F : 0.0F;
        }
        return Math.clamp((float) currentAmount / (float) requiredAmount, 0.0F, 1.0F);
    }

    public RequirementProgress nextRequirement() {
        return requirements.stream().filter(requirement -> !requirement.complete()).findFirst().orElse(null);
    }
}
