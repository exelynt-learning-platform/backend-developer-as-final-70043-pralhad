package com.example.booking.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs on EVERY incoming request (once).
 * Reads the "Authorization: Bearer <token>" header, validates the JWT,
 * and if valid → marks the request as authenticated for this user.
 * If missing/invalid → request continues unauthenticated, and Spring Security
 * later rejects it with 401 for protected endpoints.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Read the Authorization header
        final String authHeader = request.getHeader("Authorization");

        // 2. No header / wrong scheme → not our job, pass through unauthenticated
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // 3. Extract + verify token, pull the username out
            final String token = authHeader.substring(7); // skip "Bearer "
            final String username = jwtService.extractUsername(token);

            // 4. If we have a username and nobody is authenticated yet
            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 5. Signature, expiry, and username all valid?
                if (jwtService.isTokenValid(token, userDetails)) {

                    // 6. Build the Authentication object and store it for THIS request
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,                      // credentials — not needed anymore
                                    userDetails.getAuthorities()); // [ROLE_ADMIN] / [ROLE_USER]

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | UsernameNotFoundException e) {
            // Invalid/expired/forged token, or user no longer exists in DB.
            // We deliberately continue WITHOUT authentication —
            // Spring Security's entry point will send a clean 401 later.
        }

        // 7. Always continue the chain
        filterChain.doFilter(request, response);
    }
}