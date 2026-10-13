package com.historiamc.townyeras.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OnboardingStateTest {
    @Test void reminderRunsEveryMinuteUntilTreeIsViewed() {
        var state = new OnboardingState(false, false, 0);
        assertFalse(state.advance(59_999));
        assertTrue(state.advance(1));
        assertFalse(state.advance(30_000));
        assertTrue(state.advance(30_000));
        state.markTreeViewed();
        assertFalse(state.advance(600_000));
        assertEquals(0, state.pendingMillis());
    }
    @Test void reloggingRetainsAccumulatedOnlineTime() {
        var state = new OnboardingState(false, false, 0);
        state.advance(45_000);
        var restored = new OnboardingState(state.treeViewed(), state.tutorialDismissed(), state.pendingMillis());
        assertFalse(restored.advance(14_999));
        assertTrue(restored.advance(1));
    }
    @Test void skippingTourDoesNotPretendTheTreeWasViewed() {
        var state = new OnboardingState(false, false, 0);
        state.dismissTutorial();
        assertTrue(state.tutorialDismissed());
        assertFalse(state.treeViewed());
        assertTrue(state.advance(60_000));
    }
    @Test void delayedTicksNeverGenerateAReminderBacklog() {
        var state = new OnboardingState(false, false, 500);
        assertFalse(state.advance(-1));
        assertTrue(state.advance(Long.MAX_VALUE));
        assertEquals(500, state.pendingMillis());
    }
}
