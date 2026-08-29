package com.example.booking.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security when an UNAUTHENTICATED request
 * hits a protected endpoint. Writes a clean JSON 401 response.
 * (No Jackson needed — we write the JSON string directly.)
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String json = "{\"status\":401,"
                + "\"error\":\"Unauthorized\","
                + "\"message\":\"Authentication required or token invalid/expired\","
                + "\"path\":\"" + request.getRequestURI() + "\"}";

        response.getWriter().write(json);
    }
}