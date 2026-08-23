package com.dbay.teddy.manager;

import com.dbay.teddy.entity.App;
import com.dbay.teddy.entity.Job;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.service.YarnService;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class StateRefresherTest {

    @Test
    public void usesOneSnapshotAndPersistsItsStateAndRuntime() {
        YarnService yarnService = mock(YarnService.class);
        JobService jobService = mock(JobService.class);
        Job job = mock(Job.class);
        App snapshot = new App();
        snapshot.setState("RUNNING");
        snapshot.setStartedTime(1000L);
        snapshot.setFinishedTime(2000L);
        when(job.getAppId()).thenReturn("application_1_2");
        when(yarnService.app("application_1_2")).thenReturn(snapshot);

        new StateRefresher(yarnService, jobService).refresh(job);

        verify(yarnService, times(1)).app("application_1_2");
        verify(job).setState("RUNNING");
        verify(job).setTotalRunningTime("0天0小时0分钟1秒");
        verify(jobService).update(job);
    }

    @Test
    public void keepsThePersistedStateWhenYarnIsUnavailable() {
        YarnService yarnService = mock(YarnService.class);
        JobService jobService = mock(JobService.class);
        Job job = mock(Job.class);
        when(job.getAppId()).thenReturn("application_1_2");
        when(yarnService.app("application_1_2"))
                .thenThrow(new YarnService.YarnQueryException("unavailable"));

        new StateRefresher(yarnService, jobService).refresh(job);

        verify(job, never()).setState(org.mockito.ArgumentMatchers.anyString());
        verify(jobService, never()).update(job);
    }
}
