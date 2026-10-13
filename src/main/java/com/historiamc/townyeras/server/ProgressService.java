package com.historiamc.townyeras.server;

import com.historiamc.townyeras.tech.TechNode;
import com.historiamc.townyeras.tech.TechRegistry;
import com.historiamc.townyeras.tech.TechRequirement;
import com.historiamc.townyeras.tech.TechnologyManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative technology progression.
 *
 * <p>All methods are intended to run on the logical server thread. Event handlers feed
 * canonical registry IDs (and, when available, category aliases such as {@code #logs})
 * into {@link #recordAction(ServerPlayer, String, Collection, double)}. The service
 * handles eligibility, prerequisites, clamping, completion, persistence, and snapshots.</p>
 */
public final class ProgressService {
    private static final double EPSILON = 0.000_001D;

    private ProgressService() {
    }

    /**
     * Records one action against one target ID. The returned update can drive completion
     * notices and an immediate client synchronization.
     */
    public static ProgressUpdate recordAction(ServerPlayer player, String action, String target, double amount) {
        return recordAction(player, action, List.of(target == null ? "" : target), amount);
    }

    /**
     * Records one action against a canonical target plus any aliases/categories it belongs to.
     * A requirement receives credit at most once even when several supplied targets match it.
     */
    public static ProgressUpdate recordAction(
            ServerPlayer player,
            String action,
            Collection<String> targets,
            double amount
    ) {
        Objects.requireNonNull(player, "player");
        if (!ServerSessionPolicy.isActive(player.server)) {
            return ProgressUpdate.NONE;
        }
        String normalizedAction = ServerIds.normalizeAction(action);
        if (normalizedAction.isEmpty() || !Double.isFinite(amount) || amount <= 0.0D) {
            return ProgressUpdate.NONE;
        }

        LinkedHashSet<String> normalizedTargets = new LinkedHashSet<>();
        if (targets != null) {
            for (String target : targets) {
                String normalized = ServerIds.normalizeTarget(target);
                if (!normalized.isEmpty()) {
                    normalizedTargets.add(normalized);
                }
            }
        }
        if (normalizedTargets.isEmpty()) {
            return ProgressUpdate.NONE;
        }

        registry();
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        String currentEra = data.timeline().eraId();

        // Freeze eligibility at the start of the action. Completing a prerequisite with
        // this action unlocks its child for the next action rather than double-counting one act.
        List<TechNode> eligibleNodes = new ArrayList<>();
        for (TechNode node : registry().nodes().values()) {
            if (isEligibleForProgress(progress, node, currentEra)
                    && !progress.completedNodesView().contains(node.id())
                    && prerequisitesMet(progress, node)) {
                eligibleNodes.add(node);
            }
        }

        List<RequirementDelta> deltas = new ArrayList<>();
        for (TechNode node : eligibleNodes) {
            for (TechRequirement requirement : node.requirements()) {
                if (!ProgressActionPolicy.matches(ServerIds.normalizeAction(requirement.action()), normalizedAction, normalizedTargets)
                        || !matchesAny(normalizedTargets, requirement.targets())) {
                    continue;
                }
                double required = Math.max(0.0D, requirement.amount());
                double before = Math.min(required, progress.contribution(node.id(), requirement.id()));
                if (before + EPSILON >= required) {
                    continue;
                }
                if (normalizedAction.equals("distinct_fish")
                        && !progress.addDistinct(node.id(), requirement.id(), normalizedTargets.iterator().next())) {
                    continue;
                }
                double after = Math.min(required, before + amount);
                progress.setContribution(node.id(), requirement.id(), after);
                deltas.add(new RequirementDelta(node.id(), requirement.id(), before, after, required));
            }
        }

        LinkedHashSet<String> completed = completeReadyNodes(progress, currentEra);
        if (deltas.isEmpty() && completed.isEmpty()) {
            return ProgressUpdate.NONE;
        }
        data.setDirty();
        return new ProgressUpdate(true, List.copyOf(deltas), Set.copyOf(completed));
    }

    public static NodeStatus nodeStatus(ServerPlayer player, String nodeId) {
        Objects.requireNonNull(player, "player");
        return nodeStatus(player.server, player.getUUID(), nodeId);
    }

    public static NodeStatus nodeStatus(MinecraftServer server, UUID playerId, String nodeId) {
        registry();
        TechNode node = registry().node(nodeId);
        if (node == null) {
            return NodeStatus.UNKNOWN;
        }
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        PlayerProgress progress = data.player(playerId);
        if (progress.completedNodesView().contains(node.id())) {
            return NodeStatus.COMPLETED;
        }
        if (!com.historiamc.townyeras.tech.ResearchEraPolicy.available(registry().eras(), data.timeline().eraId(), node.era())) {
            return NodeStatus.LOCKED_ERA;
        }
        if (!classEligible(progress, node)) {
            return NodeStatus.LOCKED_CLASS;
        }
        if (!prerequisitesMet(progress, node)) {
            return NodeStatus.LOCKED_PREREQUISITES;
        }
        for (TechRequirement requirement : node.requirements()) {
            if (progress.contribution(node.id(), requirement.id()) > EPSILON) {
                return NodeStatus.IN_PROGRESS;
            }
        }
        return NodeStatus.AVAILABLE;
    }

    public static List<String> missingPrerequisites(ServerPlayer player, String nodeId) {
        Objects.requireNonNull(player, "player");
        return missingPrerequisites(player.server, player.getUUID(), nodeId);
    }

    public static List<String> missingPrerequisites(MinecraftServer server, UUID playerId, String nodeId) {
        registry();
        TechNode node = registry().node(nodeId);
        if (node == null) {
            return List.of();
        }
        PlayerProgress progress = HistoriaErasSavedData.get(server).player(playerId);
        List<String> missing = new ArrayList<>();
        for (String prerequisite : node.effectivePrerequisites(progress.selectedClassesView(),
                id -> hasNodeAccess(progress, id))) {
            if (!hasNodeAccess(progress, prerequisite)) {
                missing.add(prerequisite);
            }
        }
        return List.copyOf(missing);
    }

    public static boolean prerequisitesMet(ServerPlayer player, String nodeId) {
        return missingPrerequisites(player, nodeId).isEmpty() && registry().node(nodeId) != null;
    }

    /**
     * Gate check. A retained completion from an old class is deliberately inactive until
     * that class is selected again; universal completions are always active.
     */
    public static boolean hasNodeAccess(ServerPlayer player, String nodeId) {
        Objects.requireNonNull(player, "player");
        return hasNodeAccess(HistoriaErasSavedData.get(player.server).player(player.getUUID()), nodeId);
    }

    public static boolean hasNodeAccess(MinecraftServer server, UUID playerId, String nodeId) {
        return hasNodeAccess(HistoriaErasSavedData.get(server).player(playerId), nodeId);
    }

    /** Fast gate check that does not build a client snapshot. */
    public static boolean hasSelectedClass(ServerPlayer player, String classId) {
        Objects.requireNonNull(player, "player");
        String normalized = ServerIds.normalizeId(classId);
        if (normalized.equals("universal")) {
            return true;
        }
        return HistoriaErasSavedData.get(player.server)
                .player(player.getUUID())
                .selectedClassesView()
                .contains(normalized);
    }

    public static ClassChangeResult selectClass(ServerPlayer player, String classId) {
        Objects.requireNonNull(player, "player");
        registry();
        String normalized = ServerIds.normalizeId(classId);
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());

        if (!isKnownSelectableClass(normalized)) {
            return ClassChangeResult.of(ClassChangeStatus.UNKNOWN_CLASS, progress);
        }
        if (progress.selectedClassesView().contains(normalized)) {
            return ClassChangeResult.of(ClassChangeStatus.ALREADY_SELECTED, progress);
        }
        if (progress.selectedClassesView().size() >= PlayerProgress.MAX_CLASSES) {
            return ClassChangeResult.of(ClassChangeStatus.CLASS_LIMIT_REACHED, progress);
        }
        progress.mutableSelectedClasses().add(normalized);
        completeReadyNodes(progress, data.timeline().eraId());
        data.setDirty();
        return ClassChangeResult.of(ClassChangeStatus.SUCCESS, progress);
    }

    /** Atomically chooses the player's initial pair of classes. */
    public static ClassChangeResult selectClasses(ServerPlayer player, String firstClass, String secondClass) {
        Objects.requireNonNull(player, "player");
        registry();
        String first = ServerIds.normalizeId(firstClass);
        String second = ServerIds.normalizeId(secondClass);
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());

        if (first.equals(second)) {
            return ClassChangeResult.of(ClassChangeStatus.DUPLICATE_CLASS, progress);
        }
        if (!isKnownSelectableClass(first) || !isKnownSelectableClass(second)) {
            return ClassChangeResult.of(ClassChangeStatus.UNKNOWN_CLASS, progress);
        }
        if (!progress.selectedClassesView().isEmpty()) {
            return ClassChangeResult.of(ClassChangeStatus.ALREADY_CHOSEN, progress);
        }
        progress.mutableSelectedClasses().add(first);
        progress.mutableSelectedClasses().add(second);
        completeReadyNodes(progress, data.timeline().eraId());
        data.setDirty();
        return ClassChangeResult.of(ClassChangeStatus.SUCCESS, progress);
    }

    /** Replaces one active class and consumes one job token. Earned history is retained. */
    public static ClassChangeResult swapClass(ServerPlayer player, String oldClass, String newClass) {
        Objects.requireNonNull(player, "player");
        registry();
        String oldId = ServerIds.normalizeId(oldClass);
        String newId = ServerIds.normalizeId(newClass);
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());

        if (!progress.selectedClassesView().contains(oldId)) {
            return ClassChangeResult.of(ClassChangeStatus.OLD_CLASS_NOT_SELECTED, progress);
        }
        if (!isKnownSelectableClass(newId)) {
            return ClassChangeResult.of(ClassChangeStatus.UNKNOWN_CLASS, progress);
        }
        if (progress.selectedClassesView().contains(newId)) {
            return ClassChangeResult.of(ClassChangeStatus.ALREADY_SELECTED, progress);
        }
        if (progress.jobTokens() <= 0) {
            return ClassChangeResult.of(ClassChangeStatus.NO_JOB_TOKEN, progress);
        }

        LinkedHashSet<String> replacement = new LinkedHashSet<>();
        for (String selected : progress.selectedClassesView()) {
            replacement.add(selected.equals(oldId) ? newId : selected);
        }
        progress.mutableSelectedClasses().clear();
        progress.mutableSelectedClasses().addAll(replacement);
        progress.setJobTokens(progress.jobTokens() - 1);

        TechNode tracked = registry().node(progress.trackedNode());
        if (tracked != null && !tracked.classEligible(progress.selectedClassesView())) {
            progress.setTrackedNode("");
        }
        completeReadyNodes(progress, data.timeline().eraId());
        data.setDirty();
        return ClassChangeResult.of(ClassChangeStatus.SUCCESS, progress);
    }

    public static int jobTokens(ServerPlayer player) {
        return HistoriaErasSavedData.get(player.server).player(player.getUUID()).jobTokens();
    }

    public static int addJobTokens(ServerPlayer player, int amount) {
        Objects.requireNonNull(player, "player");
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        long adjusted = Math.max(0L, (long) progress.jobTokens() + amount);
        progress.setJobTokens((int) Math.min(Integer.MAX_VALUE, adjusted));
        data.setDirty();
        return progress.jobTokens();
    }

    /** Administrator override: replaces both active classes without consuming a job token. */
    public static ClassChangeResult adminSetClasses(ServerPlayer player, String firstClass, String secondClass) {
        Objects.requireNonNull(player, "player");
        registry();
        String first = ServerIds.normalizeId(firstClass);
        String second = ServerIds.normalizeId(secondClass);
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        if (first.equals(second)) {
            return ClassChangeResult.of(ClassChangeStatus.DUPLICATE_CLASS, progress);
        }
        if (!isKnownSelectableClass(first) || !isKnownSelectableClass(second)) {
            return ClassChangeResult.of(ClassChangeStatus.UNKNOWN_CLASS, progress);
        }
        progress.mutableSelectedClasses().clear();
        progress.mutableSelectedClasses().add(first);
        progress.mutableSelectedClasses().add(second);
        TechNode tracked = registry().node(progress.trackedNode());
        if (tracked != null && !tracked.classEligible(progress.selectedClassesView())) {
            progress.setTrackedNode("");
        }
        completeReadyNodes(progress, data.timeline().eraId());
        data.setDirty();
        return ClassChangeResult.of(ClassChangeStatus.SUCCESS, progress);
    }

    /** Administrator override: removes the active class pair while retaining earned history. */
    public static void adminClearClasses(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        progress.mutableSelectedClasses().clear();
        progress.setTrackedNode("");
        data.setDirty();
    }

    public static boolean trackNode(ServerPlayer player, String nodeId) {
        Objects.requireNonNull(player, "player");
        registry();
        String normalized = ServerIds.normalizeId(nodeId);
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        if (normalized.isEmpty()) {
            if (progress.trackedNode().isEmpty()) {
                return true;
            }
            progress.setTrackedNode("");
            data.setDirty();
            return true;
        }
        TechNode node = registry().node(normalized);
        if (node == null) {
            return false;
        }
        progress.setTrackedNode(node.id());
        data.setDirty();
        return true;
    }

    /** Administrator override: completes the node and fills all of its requirements. */
    public static boolean adminGrantNode(ServerPlayer player, String nodeId) {
        Objects.requireNonNull(player, "player");
        registry();
        TechNode node = registry().node(nodeId);
        if (node == null) {
            return false;
        }
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        boolean changed = progress.mutableCompletedNodes().add(node.id());
        changed |= progress.mutableGrantedNodes().add(node.id());
        for (TechRequirement requirement : node.requirements()) {
            double before = progress.contribution(node.id(), requirement.id());
            if (before + EPSILON < requirement.amount()) {
                progress.setContribution(node.id(), requirement.id(), requirement.amount());
                changed = true;
            }
        }
        if (changed) {
            data.setDirty();
        }
        return changed;
    }

    public static boolean grantNode(ServerPlayer player, String nodeId) {
        return adminGrantNode(player, nodeId);
    }

    /**
     * Administrator revoke. By default dependants are revoked too, preventing an orphaned
     * completion from granting access after one of its prerequisites is removed.
     */
    public static Set<String> adminRevokeNode(ServerPlayer player, String nodeId) {
        return adminRevokeNode(player, nodeId, true);
    }

    public static Set<String> adminRevokeNode(ServerPlayer player, String nodeId, boolean cascade) {
        Objects.requireNonNull(player, "player");
        registry();
        TechNode requested = registry().node(nodeId);
        if (requested == null) {
            return Set.of();
        }
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        LinkedHashSet<String> toRevoke = new LinkedHashSet<>();
        toRevoke.add(requested.id());

        if (cascade) {
            boolean changed;
            do {
                changed = false;
                for (TechNode node : registry().nodes().values()) {
                    if (!toRevoke.contains(node.id())
                            && node.dependsOnRevoked(progress.selectedClassesView(), toRevoke, progress.completedNodesView()::contains)) {
                        changed |= toRevoke.add(node.id());
                    }
                }
            } while (changed);
        }

        boolean changed = false;
        LinkedHashSet<String> actuallyChanged = new LinkedHashSet<>();
        for (String revokedId : toRevoke) {
            boolean nodeChanged = progress.mutableCompletedNodes().remove(revokedId);
            nodeChanged |= progress.mutableGrantedNodes().remove(revokedId);
            int contributionCount = progress.mutableContributions().size();
            progress.clearNodeContributions(revokedId);
            nodeChanged |= contributionCount != progress.mutableContributions().size();
            if (nodeChanged) {
                actuallyChanged.add(revokedId);
                changed = true;
            }
        }
        if (toRevoke.contains(progress.trackedNode())) {
            progress.setTrackedNode("");
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
        return Set.copyOf(actuallyChanged);
    }

    public static Set<String> revokeNode(ServerPlayer player, String nodeId) {
        return adminRevokeNode(player, nodeId);
    }

    /** Clears technologies and counters while preserving selected classes and job tokens. */
    public static void adminResetProgress(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        data.player(player.getUUID()).clearProgress();
        data.setDirty();
    }

    /** Clears all HistoriaEras player state, including classes and job tokens. */
    public static void adminResetEverything(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        data.player(player.getUUID()).clearEverything();
        data.setDirty();
    }

    public static void resetProgress(ServerPlayer player) {
        adminResetProgress(player);
    }

    public static void resetEverything(ServerPlayer player) {
        adminResetEverything(player);
    }

    public static PlayerSnapshot snapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return snapshot(player.server, player.getUUID(), player.getGameProfile().getName());
    }

    public static PlayerSnapshot snapshot(MinecraftServer server, UUID playerId, String playerName) {
        registry();
        HistoriaErasSavedData data = HistoriaErasSavedData.get(server);
        PlayerProgress progress = data.player(playerId);
        List<NodeSnapshot> nodes = new ArrayList<>();
        for (TechNode node : registry().nodes().values()) {
            nodes.add(nodeSnapshot(progress, data.timeline(), node));
        }
        return new PlayerSnapshot(
                playerId,
                playerName == null ? "" : playerName,
                List.copyOf(progress.selectedClassesView()),
                progress.jobTokens(),
                Set.copyOf(progress.completedNodesView()),
                progress.trackedNode(),
                Map.copyOf(progress.contributionsView()),
                TimelineService.TimelineSnapshot.from(data.timeline()),
                List.copyOf(nodes)
        );
    }

    /** NBT wire snapshot consumed by the client cache and GUI. */
    public static CompoundTag createSnapshot(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        registry();
        HistoriaErasSavedData data = HistoriaErasSavedData.get(player.server);
        PlayerProgress progress = data.player(player.getUUID());
        CompoundTag snapshot = new CompoundTag();
        snapshot.putString("playerName", player.getGameProfile().getName());
        snapshot.putString("nationName", TownyNationResolver.nationName(player));

        ListTag classes = new ListTag();
        progress.selectedClassesView().forEach(classId -> classes.add(StringTag.valueOf(classId)));
        snapshot.put("classes", classes);
        snapshot.putInt("jobTokens", progress.jobTokens());
        boolean operator = player.createCommandSourceStack().hasPermission(3)
                || Boolean.getBoolean("townyeras.prototype.visualTestAdmin");
        boolean dashboardAdmin = AdminModeService.isEnabled(player)
                || Boolean.getBoolean("townyeras.prototype.visualTestAdmin");
        snapshot.putBoolean("operator", operator);
        snapshot.putBoolean("adminMode", dashboardAdmin);
        snapshot.putBoolean("timelineAdmin", dashboardAdmin);
        ListTag onlinePlayers = new ListTag();
        if (dashboardAdmin) {
            player.server.getPlayerList().getPlayers().stream()
                    .map(online -> online.getGameProfile().getName())
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(name -> onlinePlayers.add(StringTag.valueOf(name)));
        }
        snapshot.put("onlinePlayers", onlinePlayers);

        ListTag completedNodes = new ListTag();
        progress.completedNodesView().forEach(nodeId -> completedNodes.add(StringTag.valueOf(nodeId)));
        snapshot.put("completedNodes", completedNodes);
        ListTag grantedNodes = new ListTag();
        progress.grantedNodesView().forEach(nodeId -> grantedNodes.add(StringTag.valueOf(nodeId)));
        snapshot.put("grantedNodes", grantedNodes);
        snapshot.putString("trackedNode", progress.trackedNode());
        snapshot.putBoolean("techTreeViewed", progress.onboarding().treeViewed());
        snapshot.putBoolean("tutorialDismissed", progress.onboarding().tutorialDismissed());

        CompoundTag contributions = new CompoundTag();
        progress.contributionsView().forEach(contributions::putDouble);
        snapshot.put("contributions", contributions);
        snapshot.put("timeline", TimelineService.writeSnapshot(new CompoundTag(), data.timeline()));

        // Extra derived fields keep the client presentation simple without granting it any
        // authority. Older clients can safely ignore these additions.
        CompoundTag statuses = new CompoundTag();
        ListTag accessibleNodes = new ListTag();
        for (TechNode node : registry().nodes().values()) {
            NodeSnapshot nodeSnapshot = nodeSnapshot(progress, data.timeline(), node);
            statuses.putString(node.id(), nodeSnapshot.status().name().toLowerCase(Locale.ROOT));
            if (nodeSnapshot.accessible()) {
                accessibleNodes.add(StringTag.valueOf(node.id()));
            }
        }
        snapshot.put("nodeStatuses", statuses);
        snapshot.put("accessibleNodes", accessibleNodes);
        return snapshot;
    }

    private static NodeSnapshot nodeSnapshot(PlayerProgress progress, TimelineState timeline, TechNode node) {
        List<String> missingPrerequisites = new ArrayList<>();
        for (String prerequisite : node.effectivePrerequisites(progress.selectedClassesView(),
                id -> hasNodeAccess(progress, id))) {
            if (!hasNodeAccess(progress, prerequisite)) {
                missingPrerequisites.add(prerequisite);
            }
        }

        NodeStatus status;
        if (progress.completedNodesView().contains(node.id())) {
            status = NodeStatus.COMPLETED;
        } else if (!com.historiamc.townyeras.tech.ResearchEraPolicy.available(registry().eras(), timeline.eraId(), node.era())) {
            status = NodeStatus.LOCKED_ERA;
        } else if (!classEligible(progress, node)) {
            status = NodeStatus.LOCKED_CLASS;
        } else if (!missingPrerequisites.isEmpty()) {
            status = NodeStatus.LOCKED_PREREQUISITES;
        } else if (node.requirements().stream().anyMatch(requirement ->
                progress.contribution(node.id(), requirement.id()) > EPSILON)) {
            status = NodeStatus.IN_PROGRESS;
        } else {
            status = NodeStatus.AVAILABLE;
        }

        List<RequirementSnapshot> requirements = new ArrayList<>();
        double progressTotal = 0.0D;
        double requiredTotal = 0.0D;
        for (TechRequirement requirement : node.requirements()) {
            double required = Math.max(0.0D, requirement.amount());
            double current = Math.clamp(progress.contribution(node.id(), requirement.id()), 0.0D, required);
            progressTotal += current;
            requiredTotal += required;
            requirements.add(new RequirementSnapshot(
                    requirement.id(),
                    requirement.display(),
                    current,
                    required,
                    current + EPSILON >= required
            ));
        }
        double fraction = requiredTotal <= EPSILON
                ? (status == NodeStatus.COMPLETED ? 1.0D : 0.0D)
                : Math.clamp(progressTotal / requiredTotal, 0.0D, 1.0D);
        return new NodeSnapshot(
                node.id(),
                status,
                hasNodeAccess(progress, node.id()),
                List.copyOf(missingPrerequisites),
                List.copyOf(requirements),
                fraction
        );
    }

    private static LinkedHashSet<String> completeReadyNodes(PlayerProgress progress, String currentEra) {
        LinkedHashSet<String> newlyCompleted = new LinkedHashSet<>();
        boolean completedOne;
        do {
            completedOne = false;
            for (TechNode node : registry().nodes().values()) {
                if (progress.completedNodesView().contains(node.id())
                        || !isEligibleForProgress(progress, node, currentEra)
                        || !prerequisitesMet(progress, node)
                        || !requirementsComplete(progress, node)) {
                    continue;
                }
                progress.mutableCompletedNodes().add(node.id());
                newlyCompleted.add(node.id());
                completedOne = true;
            }
        } while (completedOne);
        return newlyCompleted;
    }

    private static boolean requirementsComplete(PlayerProgress progress, TechNode node) {
        for (TechRequirement requirement : node.requirements()) {
            if (progress.contribution(node.id(), requirement.id()) + EPSILON < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private static boolean prerequisitesMet(PlayerProgress progress, TechNode node) {
        for (String prerequisite : node.effectivePrerequisites(progress.selectedClassesView(),
                id -> hasNodeAccess(progress, id))) {
            if (!hasNodeAccess(progress, prerequisite)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNodeAccess(PlayerProgress progress, String nodeId) {
        TechNode node = registry().node(nodeId);
        return node != null
                && progress.completedNodesView().contains(node.id())
                && (progress.grantedNodesView().contains(node.id()) || classEligible(progress, node));
    }

    private static boolean isEligibleForProgress(PlayerProgress progress, TechNode node, String currentEra) {
        return com.historiamc.townyeras.tech.ResearchEraPolicy.available(registry().eras(), currentEra, node.era())
                && classEligible(progress, node);
    }

    private static boolean classEligible(PlayerProgress progress, TechNode node) {
        String classId = ServerIds.normalizeId(node.classId());
        return node.classEligible(progress.selectedClassesView());
    }

    private static boolean isKnownSelectableClass(String classId) {
        if (classId.isEmpty() || classId.equals("universal")) {
            return false;
        }
        var definition = registry().classes().get(classId);
        return definition != null && definition.enabled();
    }

    private static boolean matchesAny(Set<String> eventTargets, List<String> requirementTargets) {
        if (requirementTargets == null || requirementTargets.isEmpty()) {
            return false;
        }
        for (String rawPattern : requirementTargets) {
            String pattern = ServerIds.normalizeTarget(rawPattern);
            for (String target : eventTargets) {
                if (registry().matches(target, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TechRegistry registry() {
        return TechnologyManager.current();
    }

    public enum NodeStatus {
        COMPLETED,
        IN_PROGRESS,
        AVAILABLE,
        LOCKED_PREREQUISITES,
        LOCKED_CLASS,
        LOCKED_ERA,
        UNKNOWN
    }

    public enum ClassChangeStatus {
        SUCCESS,
        UNKNOWN_CLASS,
        DUPLICATE_CLASS,
        ALREADY_SELECTED,
        ALREADY_CHOSEN,
        CLASS_LIMIT_REACHED,
        OLD_CLASS_NOT_SELECTED,
        NO_JOB_TOKEN
    }

    public record ClassChangeResult(ClassChangeStatus status, List<String> selectedClasses, int remainingJobTokens) {
        static ClassChangeResult of(ClassChangeStatus status, PlayerProgress progress) {
            return new ClassChangeResult(status, List.copyOf(progress.selectedClassesView()), progress.jobTokens());
        }

        public boolean successful() {
            return status == ClassChangeStatus.SUCCESS;
        }
    }

    public record RequirementDelta(
            String nodeId,
            String requirementId,
            double before,
            double after,
            double required
    ) {
    }

    public record ProgressUpdate(
            boolean changed,
            List<RequirementDelta> requirementDeltas,
            Set<String> completedNodeIds
    ) {
        public static final ProgressUpdate NONE = new ProgressUpdate(false, List.of(), Set.of());

        public ProgressUpdate {
            requirementDeltas = List.copyOf(requirementDeltas);
            completedNodeIds = Set.copyOf(completedNodeIds);
        }
    }

    public record RequirementSnapshot(
            String id,
            String display,
            double progress,
            double required,
            boolean completed
    ) {
    }

    public record NodeSnapshot(
            String id,
            NodeStatus status,
            boolean accessible,
            List<String> missingPrerequisites,
            List<RequirementSnapshot> requirements,
            double progressFraction
    ) {
        public NodeSnapshot {
            missingPrerequisites = List.copyOf(missingPrerequisites);
            requirements = List.copyOf(requirements);
        }
    }

    public record PlayerSnapshot(
            UUID playerId,
            String playerName,
            List<String> selectedClasses,
            int jobTokens,
            Set<String> completedNodeIds,
            String trackedNodeId,
            Map<String, Double> contributions,
            TimelineService.TimelineSnapshot timeline,
            List<NodeSnapshot> nodes
    ) {
        public PlayerSnapshot {
            selectedClasses = List.copyOf(selectedClasses);
            completedNodeIds = Set.copyOf(completedNodeIds);
            contributions = Map.copyOf(contributions);
            nodes = List.copyOf(nodes);
        }
    }
}
