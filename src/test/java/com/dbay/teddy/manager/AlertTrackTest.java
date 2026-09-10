package com.dbay.teddy.manager;

import com.dbay.teddy.manager.AlertTrack.Action;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AlertTrackTest {

    private static final long SECOND = 1000L;
    private static final long SCAN_INTERVAL = 60L;

    @Test
    public void notifiesImmediatelyThenBacksOffToTheCap() {
        AlertTrack track = new AlertTrack("RUNNING", SCAN_INTERVAL);
        assertEquals(Action.NONE, track.observe("RUNNING", 0L));

        assertEquals(Action.FAILURE, track.observe("FAILED", 0L));
        assertEquals(60L, track.markNotified(0L));
        assertEquals(1, track.notifyCount());
        assertEquals(60L * SECOND, track.nextNotifyAt());

        long[] followingIntervals = {120L, 300L, 600L, 600L};
        for (long interval : followingIntervals) {
            long due = track.nextNotifyAt();
            assertEquals(Action.NONE, track.observe("FAILED", due - 1));
            assertEquals(Action.FAILURE, track.observe("FAILED", due));
            assertEquals(interval, track.markNotified(due));
            assertEquals(due + interval * SECOND, track.nextNotifyAt());
        }
        assertEquals(5, track.notifyCount());
    }

    @Test
    public void backoffFollowsTheConfiguredScanInterval() {
        AlertTrack track = new AlertTrack("RUNNING", 10L);

        assertEquals(Action.FAILURE, track.observe("FAILED", 0L));
        assertEquals(10L, track.markNotified(0L));
        assertEquals(10L * SECOND, track.nextNotifyAt());

        long due = track.nextNotifyAt();
        assertEquals(Action.NONE, track.observe("FAILED", due - 1));
        assertEquals(Action.FAILURE, track.observe("FAILED", due));
        assertEquals(20L, track.markNotified(due));
        assertEquals(due + 20L * SECOND, track.nextNotifyAt());

        due = track.nextNotifyAt();
        assertEquals(Action.FAILURE, track.observe("FAILED", due));
        assertEquals(50L, track.markNotified(due));
    }

    @Test
    public void reportsRecoveryWithTheFailureCountAndStartsOverAfterwards() {
        AlertTrack track = new AlertTrack("FAILED", SCAN_INTERVAL);
        assertEquals(60L, track.markNotified(0L));
        assertEquals(Action.FAILURE, track.observe("FAILED", 60L * SECOND));
        assertEquals(120L, track.markNotified(60L * SECOND));
        assertEquals(2, track.notifyCount());

        assertEquals(Action.RECOVERY, track.observe("RUNNING", 61L * SECOND));
        assertEquals(2, track.notifyCount());
        track.reset();

        assertEquals(Action.NONE, track.observe("RUNNING", 62L * SECOND));

        assertEquals(Action.FAILURE, track.observe("FAILED", 63L * SECOND));
        assertEquals(60L, track.markNotified(63L * SECOND));
        assertEquals(1, track.notifyCount());
    }

    @Test
    public void doesNotTreatOtherTransitionsAsRecoveryOrFailure() {
        AlertTrack killed = new AlertTrack("FAILED", SCAN_INTERVAL);
        assertEquals(Action.NONE, killed.observe("KILLED", 0L));

        AlertTrack transientState = new AlertTrack("RUNNING", SCAN_INTERVAL);
        assertEquals(Action.NONE, transientState.observe("SUBMITTED", 0L));
        assertEquals(Action.FAILURE, transientState.observe("FAILED", 0L));
    }

    @Test
    public void repeatedRunningObservationsStayQuiet() {
        AlertTrack track = new AlertTrack("RUNNING", SCAN_INTERVAL);
        for (long now = 0L; now < 10 * 60 * SECOND; now += 60 * SECOND) {
            assertEquals(Action.NONE, track.observe("RUNNING", now));
        }
        assertEquals(0, track.notifyCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveScanInterval() {
        new AlertTrack("RUNNING", 0L);
    }
}
