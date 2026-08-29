package com.example.booking.service;

import com.example.booking.dto.LoginRequest;
import com.example.booking.dto.LoginResponse;
import com.example.booking.security.JwtService;
import com.example.booking.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        // 1. Hand credentials to Spring Security — it does the DB lookup + BCrypt compare
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()));

        // 2. authenticate() threw if wrong — reaching here means credentials are valid
        UserDetailsImpl principal = (UserDetailsImpl) authentication.getPrincipal();

        // 3. Mint the JWT
        String token = jwtService.generateToken(principal);

        log.info("User '{}' logged in successfully", request.getUsername());

        return LoginResponse.builder()
                .token(token)
                .type("Bearer")
                .username(principal.getUsername())
                .role(principal.getUser().getRole().name())
                .build();
    }

}