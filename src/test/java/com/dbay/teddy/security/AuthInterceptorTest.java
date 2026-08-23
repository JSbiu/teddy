package com.dbay.teddy.security;

import com.dbay.teddy.service.LoginService;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AuthInterceptorTest {
    @Test
    public void rejectsRequestsWithoutAValidServerSession() throws Exception {
        LoginService loginService = mock(LoginService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(loginService.getCookieValue(request, LoginService.SESSION_COOKIE_NAME)).thenReturn(null);
        when(loginService.checkToken(null)).thenReturn(false);

        boolean allowed = new AuthInterceptor(loginService).preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(401, response.getStatus());
        assertEquals("{\"state\":\"error\",\"data\":\"401\"}", response.getContentAsString());
    }

    @Test
    public void allowsRequestsWithAValidServerSession() throws Exception {
        LoginService loginService = mock(LoginService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(loginService.getCookieValue(request, LoginService.SESSION_COOKIE_NAME)).thenReturn("session");
        when(loginService.checkToken("session")).thenReturn(true);

        assertTrue(new AuthInterceptor(loginService).preHandle(request, response, new Object()));
    }
}
