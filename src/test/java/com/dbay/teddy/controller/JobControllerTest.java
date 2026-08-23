package com.dbay.teddy.controller;

import com.dbay.teddy.entity.Job;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import org.junit.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JobControllerTest {

    @Test
    public void mutationEndpointsAcceptOnlyPost() throws Exception {
        assertPostOnly(JobController.class.getMethod("delete", Integer.class));
        assertPostOnly(JobController.class.getMethod("stop", Integer.class));
        assertPostOnly(JobController.class.getMethod("restart", Integer.class));
        assertPostOnly(ResourceController.class.getMethod("upload", MultipartFile.class));
        assertPostOnly(ResourceController.class.getMethod("delete", String.class));
    }

    @Test
    public void unknownTasksCannotBeDeletedOrRestarted() {
        JobService jobService = mock(JobService.class);
        when(jobService.findOne(99)).thenReturn(null);
        JobController controller = new JobController(jobService);

        Response delete = controller.delete(99);
        Response restart = controller.restart(99);

        assertEquals("error", delete.getState());
        assertEquals("error", restart.getState());
        verify(jobService, never()).delete(anyInt());
        verify(jobService, never()).restart(any(Job.class));
    }

    @Test
    public void runningTasksCannotBeDeletedOrRestarted() {
        JobService jobService = mock(JobService.class);
        Job running = mock(Job.class);
        when(running.getState()).thenReturn("RUNNING");
        when(jobService.findOne(7)).thenReturn(running);
        JobController controller = new JobController(jobService);

        assertEquals("error", controller.delete(7).getState());
        assertEquals("error", controller.restart(7).getState());

        verify(jobService, never()).delete(anyInt());
        verify(jobService, never()).restart(any(Job.class));
    }

    private void assertPostOnly(Method method) {
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        assertArrayEquals(new RequestMethod[]{RequestMethod.POST}, mapping.method());
    }
}
