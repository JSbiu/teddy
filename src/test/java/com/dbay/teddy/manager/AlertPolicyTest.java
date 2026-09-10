package com.dbay.teddy.manager;

import com.dbay.teddy.entity.Job;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AlertPolicyTest {

    @Test
    public void repeatIntervalGrowsThenStaysAtTheCap() {
        assertEquals(60L, AlertPolicy.repeatIntervalSeconds(1));
        assertEquals(120L, AlertPolicy.repeatIntervalSeconds(2));
        assertEquals(300L, AlertPolicy.repeatIntervalSeconds(3));
        assertEquals(600L, AlertPolicy.repeatIntervalSeconds(4));
        assertEquals(600L, AlertPolicy.repeatIntervalSeconds(5));
        assertEquals(600L, AlertPolicy.repeatIntervalSeconds(50));
    }

    @Test(expected = IllegalArgumentException.class)
    public void repeatIntervalRejectsNonPositiveCount() {
        AlertPolicy.repeatIntervalSeconds(0);
    }

    @Test
    public void treatsOnlyFailedAsFailure() {
        assertTrue(AlertPolicy.isFailure("FAILED"));
        assertTrue(AlertPolicy.isFailure(" failed "));

        for (String state : new String[]{"NEW", "SUBMITTED", "ACCEPTED",
                "RUNNING", "FINISHED", "KILLED", "UNKNOWN"}) {
            assertFalse(state, AlertPolicy.isFailure(state));
        }
    }

    @Test
    public void recoveryNeedsFailureBeforeAndRunningNow() {
        assertTrue(AlertPolicy.isRecovered("FAILED", "RUNNING"));
        assertTrue(AlertPolicy.isRecovered("FAILED", "running"));

        assertFalse(AlertPolicy.isRecovered("FAILED", "KILLED"));
        assertFalse(AlertPolicy.isRecovered("FAILED", "FINISHED"));
        assertFalse(AlertPolicy.isRecovered("FAILED", "SUBMITTED"));
        assertFalse(AlertPolicy.isRecovered("RUNNING", "RUNNING"));
        assertFalse(AlertPolicy.isRecovered(null, "RUNNING"));
    }

    @Test
    public void escalatesToAFaultOnceAttemptsAreExhausted() {
        assertTrue(AlertPolicy.willRestartAutomatically(configuredJob(1, 2)));
        assertTrue(AlertPolicy.willRestartAutomatically(configuredJob(1, 1)));

        assertFalse(AlertPolicy.willRestartAutomatically(configuredJob(1, 0)));
        assertFalse(AlertPolicy.willRestartAutomatically(configuredJob(0, 2)));
        assertFalse(AlertPolicy.willRestartAutomatically(configuredJob(1, null)));
        assertFalse(AlertPolicy.willRestartAutomatically(null));
    }

    private Job configuredJob(Integer restart, Integer retries) {
        Job job = mock(Job.class);
        when(job.getRestart()).thenReturn(restart);
        when(job.getRetries()).thenReturn(retries);
        return job;
    }
}
