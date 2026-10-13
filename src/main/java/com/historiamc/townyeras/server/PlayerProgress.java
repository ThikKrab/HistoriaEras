package com.historiamc.townyeras.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable server-owned progress for one player. Mutations are deliberately package-private;
 * callers should use {@link ProgressService} so invariants and dirty tracking stay intact.
 */
final class PlayerProgress {
    static final int MAX_CLASSES = 2;
    private static final String PROGRESS_SEPARATOR = "|";

    private final LinkedHashSet<String> selectedClasses = new LinkedHashSet<>();
    private final LinkedHashSet<String> completedNodes = new LinkedHashSet<>();
    private final LinkedHashSet<String> grantedNodes = new LinkedHashSet<>();
    private final LinkedHashMap<String, Double> contributions = new LinkedHashMap<>();
    private final LinkedHashMap<String, LinkedHashSet<String>> distinctTargets = new LinkedHashMap<>();
    private int jobTokens;
    private String trackedNode = "";
    private boolean adminMode;
    private OnboardingState onboarding = new OnboardingState(false, false, 0);

    static PlayerProgress load(CompoundTag tag) {
        PlayerProgress progress = new PlayerProgress();

        ListTag classes = tag.getList("classes", Tag.TAG_STRING);
        for (int i = 0; i < classes.size() && progress.selectedClasses.size() < MAX_CLASSES; i++) {
            String classId = ServerIds.normalizeId(classes.getString(i));
            if (!classId.isEmpty() && !classId.equals("universal")) {
                progress.selectedClasses.add(classId);
            }
        }

        progress.jobTokens = Math.max(0, tag.getInt("jobTokens"));
        progress.adminMode = tag.getBoolean("adminMode");
        progress.onboarding = new OnboardingState(tag.getBoolean("techTreeViewed"),
                tag.getBoolean("tutorialDismissed"), tag.getLong("onboardingMillis"));

        ListTag completed = tag.getList("completedNodes", Tag.TAG_STRING);
        for (int i = 0; i < completed.size(); i++) {
            String nodeId = ServerIds.normalizeId(completed.getString(i));
            if (!nodeId.isEmpty()) {
                progress.completedNodes.add(nodeId);
            }
        }

        progress.trackedNode = ServerIds.normalizeId(tag.getString("trackedNode"));
        ListTag granted = tag.getList("grantedNodes", Tag.TAG_STRING);
        for (int i = 0; i < granted.size(); i++) {
            String id = ServerIds.normalizeId(granted.getString(i));
            if (!id.isEmpty()) {
                progress.grantedNodes.add(id);
                progress.completedNodes.add(id);
            }
        }

        CompoundTag savedContributions = tag.getCompound("contributions");
        for (String key : savedContributions.getAllKeys()) {
            String normalizedKey = normalizeProgressKey(key);
            double value = savedContributions.getDouble(key);
            if (!normalizedKey.isEmpty() && Double.isFinite(value) && value > 0.0D) {
                progress.contributions.put(normalizedKey, value);
            }
        }
        CompoundTag distinct = tag.getCompound("distinctTargets");
        for (String key : distinct.getAllKeys()) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            ListTag list = distinct.getList(key, Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) values.add(list.getString(i));
            progress.distinctTargets.put(key, values);
        }
        return progress;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag classes = new ListTag();
        selectedClasses.forEach(value -> classes.add(StringTag.valueOf(value)));
        tag.put("classes", classes);
        tag.putInt("jobTokens", jobTokens);
        tag.putBoolean("adminMode", adminMode);
        tag.putBoolean("techTreeViewed", onboarding.treeViewed());
        tag.putBoolean("tutorialDismissed", onboarding.tutorialDismissed());
        tag.putLong("onboardingMillis", onboarding.pendingMillis());

        ListTag completed = new ListTag();
        completedNodes.forEach(value -> completed.add(StringTag.valueOf(value)));
        tag.put("completedNodes", completed);
        ListTag granted = new ListTag();
        grantedNodes.forEach(value -> granted.add(StringTag.valueOf(value)));
        tag.put("grantedNodes", granted);
        tag.putString("trackedNode", trackedNode);

        CompoundTag savedContributions = new CompoundTag();
        contributions.forEach(savedContributions::putDouble);
        tag.put("contributions", savedContributions);
        CompoundTag distinct = new CompoundTag();
        distinctTargets.forEach((key, values) -> {
            ListTag list = new ListTag();
            values.forEach(value -> list.add(StringTag.valueOf(value)));
            distinct.put(key, list);
        });
        tag.put("distinctTargets", distinct);
        return tag;
    }

    Set<String> selectedClassesView() {
        return Collections.unmodifiableSet(selectedClasses);
    }

    Set<String> completedNodesView() {
        return Collections.unmodifiableSet(completedNodes);
    }

    Map<String, Double> contributionsView() {
        return Collections.unmodifiableMap(contributions);
    }

    LinkedHashSet<String> mutableSelectedClasses() {
        return selectedClasses;
    }

    LinkedHashSet<String> mutableCompletedNodes() {
        return completedNodes;
    }

    Set<String> grantedNodesView() { return Collections.unmodifiableSet(grantedNodes); }
    LinkedHashSet<String> mutableGrantedNodes() { return grantedNodes; }

    LinkedHashMap<String, Double> mutableContributions() {
        return contributions;
    }

    int jobTokens() {
        return jobTokens;
    }

    void setJobTokens(int jobTokens) {
        this.jobTokens = Math.max(0, jobTokens);
    }

    String trackedNode() {
        return trackedNode;
    }

    void setTrackedNode(String trackedNode) {
        this.trackedNode = ServerIds.normalizeId(trackedNode);
    }

    boolean adminMode() {
        return adminMode;
    }

    OnboardingState onboarding() { return onboarding; }

    void setAdminMode(boolean adminMode) {
        this.adminMode = adminMode;
    }

    double contribution(String nodeId, String requirementId) {
        return contributions.getOrDefault(progressKey(nodeId, requirementId), 0.0D);
    }

    void setContribution(String nodeId, String requirementId, double amount) {
        String key = progressKey(nodeId, requirementId);
        if (key.isEmpty() || !Double.isFinite(amount) || amount <= 0.0D) {
            contributions.remove(key);
        } else {
            contributions.put(key, amount);
        }
    }

    void clearNodeContributions(String nodeId) {
        String prefix = ServerIds.normalizeId(nodeId) + PROGRESS_SEPARATOR;
        contributions.keySet().removeIf(key -> key.startsWith(prefix));
        distinctTargets.keySet().removeIf(key -> key.startsWith(prefix));
    }

    boolean addDistinct(String nodeId, String requirementId, String target) {
        return distinctTargets.computeIfAbsent(progressKey(nodeId, requirementId), ignored -> new LinkedHashSet<>())
                .add(target);
    }

    void clearProgress() {
        completedNodes.clear();
        grantedNodes.clear();
        contributions.clear();
        distinctTargets.clear();
        trackedNode = "";
    }

    void clearEverything() {
        selectedClasses.clear();
        jobTokens = 0;
        clearProgress();
    }

    static String progressKey(String nodeId, String requirementId) {
        String node = ServerIds.normalizeId(nodeId);
        String requirement = ServerIds.normalizeId(requirementId);
        if (node.isEmpty() || requirement.isEmpty()) {
            return "";
        }
        return node + PROGRESS_SEPARATOR + requirement;
    }

    private static String normalizeProgressKey(String key) {
        if (key == null) {
            return "";
        }
        int separator = key.indexOf(PROGRESS_SEPARATOR);
        if (separator <= 0 || separator >= key.length() - 1) {
            return "";
        }
        return progressKey(key.substring(0, separator), key.substring(separator + 1));
    }
}
