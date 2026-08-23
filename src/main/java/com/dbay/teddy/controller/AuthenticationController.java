package com.dbay.teddy.controller;

import com.dbay.teddy.service.LoginService;
import com.dbay.teddy.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("teddy")
public class AuthenticationController {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final LoginService loginService;

    public AuthenticationController(LoginService loginService) {
        this.loginService = loginService;
    }

    @RequestMapping(value = "login", method = RequestMethod.POST)
    public Response login(HttpServletRequest request, HttpServletResponse response) {
        if (!loginService.isConfigured()) {
            logger.error("Authentication is not configured");
            return Response.ERROR("authentication is not configured");
        }
        String userName = request.getParameter("userName");
        String password = request.getParameter("password");
        if (!loginService.checkPassword(userName, password)) {
            logger.warn("Login failed");
            return Response.ERROR("401");
        }

        String token = loginService.createToken();
        writeSessionCookie(response, token, loginService.getSessionMaxAgeSeconds());
        logger.info("Login succeeded");
        return Response.SUCCESS("success");
    }

    @RequestMapping(value = "checkToken", method = RequestMethod.POST)
    public Response checkToken(HttpServletRequest request) {
        String token = loginService.getCookieValue(request, LoginService.SESSION_COOKIE_NAME);
        return loginService.checkToken(token)
                ? Response.SUCCESS("success")
                : Response.ERROR("401");
    }

    @RequestMapping(value = "logout", method = RequestMethod.POST)
    public Response logout(HttpServletRequest request, HttpServletResponse response) {
        String token = loginService.getCookieValue(request, LoginService.SESSION_COOKIE_NAME);
        loginService.invalidateToken(token);
        writeSessionCookie(response, "", 0);
        return Response.SUCCESS("success");
    }

    private void writeSessionCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        StringBuilder header = new StringBuilder(LoginService.SESSION_COOKIE_NAME)
                .append('=').append(token)
                .append("; Path=/; Max-Age=").append(maxAgeSeconds)
                .append("; HttpOnly; SameSite=Lax");
        if (loginService.isSecureCookie()) {
            header.append("; Secure");
        }
        response.addHeader("Set-Cookie", header.toString());
    }
}
