package com.example.booking.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Called by Spring Security when an AUTHENTICATED user
 * tries to access something their ROLE doesn't allow.
 * Writes a clean JSON 403 response.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String json = "{\"status\":403,"
                + "\"error\":\"Forbidden\","
                + "\"message\":\"You do not have permission to perform this action\","
                + "\"path\":\"" + request.getRequestURI() + "\"}";

        response.getWriter().write(json);
    }
}