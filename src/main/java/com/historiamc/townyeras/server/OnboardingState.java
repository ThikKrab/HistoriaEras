package com.historiamc.townyeras.server;

/** Server-persisted onboarding, independent of classes or research resets. */
public final class OnboardingState {
    public static final long REMINDER_INTERVAL_MILLIS = 60_000;
    private boolean treeViewed;
    private boolean tutorialDismissed;
    private long pendingMillis;

    public OnboardingState(boolean treeViewed, boolean tutorialDismissed, long pendingMillis) {
        this.treeViewed = treeViewed;
        this.tutorialDismissed = tutorialDismissed;
        this.pendingMillis = Math.clamp(pendingMillis, 0L, REMINDER_INTERVAL_MILLIS - 1);
    }
    public boolean treeViewed() { return treeViewed; }
    public boolean tutorialDismissed() { return tutorialDismissed; }
    public long pendingMillis() { return pendingMillis; }
    public void markTreeViewed() { treeViewed = true; pendingMillis = 0; }
    public void dismissTutorial() { tutorialDismissed = true; }
    public boolean advance(long onlineMillis) {
        if (treeViewed || onlineMillis <= 0) return false;
        // A delayed tick generates one reminder, not a backlog of chat messages.
        long elapsed = Math.min(onlineMillis, REMINDER_INTERVAL_MILLIS);
        pendingMillis += elapsed;
        if (pendingMillis < REMINDER_INTERVAL_MILLIS) return false;
        pendingMillis %= REMINDER_INTERVAL_MILLIS;
        return true;
    }
}
