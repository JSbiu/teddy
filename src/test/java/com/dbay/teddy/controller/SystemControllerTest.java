package com.dbay.teddy.controller;

import com.dbay.teddy.service.JobService;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SystemControllerTest {

    @Test
    public void healthIsUpWhenDatabaseIsAvailable() {
        JobService jobService = mock(JobService.class);
        when(jobService.count()).thenReturn(5);

        ResponseEntity<Map<String, Object>> response = new SystemController(jobService).health();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("UP", response.getBody().get("database"));
    }

    @Test
    public void healthIsUnavailableWhenDatabaseCheckFails() {
        JobService jobService = mock(JobService.class);
        when(jobService.count()).thenReturn(-1);

        ResponseEntity<Map<String, Object>> response = new SystemController(jobService).health();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals("DOWN", response.getBody().get("database"));
    }
}
