package com.dbay.teddy.controller;

import com.dbay.teddy.service.LoginService;
import com.dbay.teddy.utils.Response;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoginControllerSecurityTest {
    @Test
    public void successfulLoginSetsAnHttpOnlyCookieWithoutReturningTheToken() throws Exception {
        LoginService loginService = mock(LoginService.class);
        when(loginService.isConfigured()).thenReturn(true);
        when(loginService.checkPassword("admin", "secret")).thenReturn(true);
        when(loginService.createToken()).thenReturn("random-session-token");
        when(loginService.getSessionMaxAgeSeconds()).thenReturn(3600);
        when(loginService.isSecureCookie()).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("userName", "admin");
        request.setParameter("password", "secret");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        Response response = new AuthenticationController(loginService).login(request, servletResponse);

        assertEquals("success", response.getState());
        assertEquals("success", response.getData());
        assertFalse("random-session-token".equals(response.getData()));
        String cookie = servletResponse.getHeader("Set-Cookie");
        assertNotNull(cookie);
        assertTrue(cookie.contains("token=random-session-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
    }
}
