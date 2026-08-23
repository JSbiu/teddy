package com.dbay.teddy.manager;

import com.dbay.teddy.entity.App;
import com.dbay.teddy.entity.Job;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JobStatePolicyTest {

    @Test
    public void preservesTransitionalAndSuccessfulStates() {
        assertEquals("ACCEPTED", persisted("accepted", "UNDEFINED"));
        assertEquals("RUNNING", persisted("RUNNING", "UNDEFINED"));
        assertEquals("FINISHED", persisted("FINISHED", "SUCCEEDED"));
    }

    @Test
    public void mapsFinishedFailureToTheFailureTerminalState() {
        assertEquals("FAILED", persisted("FINISHED", "FAILED"));
        assertEquals("KILLED", persisted("FINISHED", "KILLED"));
    }

    @Test
    public void restartsAndAlertsOnlyExplicitFailures() {
        Job failed = configuredJob("FAILED", 1, 1, 2);
        assertTrue(JobStatePolicy.shouldAutoRestart(failed));
        assertTrue(JobStatePolicy.shouldAlert(failed));

        for (String state : new String[]{"NEW", "SUBMITTED", "ACCEPTED",
                "RUNNING", "FINISHED", "KILLED", "UNKNOWN"}) {
            Job job = configuredJob(state, 1, 1, 2);
            assertFalse(state, JobStatePolicy.shouldAutoRestart(job));
            assertFalse(state, JobStatePolicy.shouldAlert(job));
        }
    }

    @Test
    public void requiresFlagsAndRemainingRetries() {
        assertFalse(JobStatePolicy.shouldAutoRestart(configuredJob("FAILED", 0, 1, 2)));
        assertFalse(JobStatePolicy.shouldAutoRestart(configuredJob("FAILED", 1, 1, 0)));
        assertFalse(JobStatePolicy.shouldAlert(configuredJob("FAILED", 1, 0, 2)));
    }

    private String persisted(String state, String finalStatus) {
        App app = new App();
        app.setState(state);
        app.setFinalStatus(finalStatus);
        return JobStatePolicy.persistedState(app);
    }

    private Job configuredJob(String state, int restart, int send, int retries) {
        Job job = mock(Job.class);
        when(job.getState()).thenReturn(state);
        when(job.getRestart()).thenReturn(restart);
        when(job.getSend()).thenReturn(send);
        when(job.getRetries()).thenReturn(retries);
        return job;
    }
}
