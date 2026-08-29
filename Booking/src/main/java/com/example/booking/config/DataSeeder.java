package com.example.booking.config;

import com.example.booking.entity.Resource;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Runs once at every startup. Seeds demo users and resources
 * ONLY if the tables are empty (idempotent — safe on every restart).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ResourceRepository resourceRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        seedUsers();
        seedResources();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            log.info("Users already exist — skipping user seed");
            return;
        }

        User admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .build();

        User user = User.builder()
                .username("john")
                .password(passwordEncoder.encode("john123"))
                .role(Role.USER)
                .build();

        userRepository.saveAll(List.of(admin, user));
        log.info("Seeded users → admin/admin123 (ADMIN), john/john123 (USER)");
    }

    private void seedResources() {
        if (resourceRepository.count() > 0) {
            log.info("Resources already exist — skipping resource seed");
            return;
        }

        List<Resource> resources = List.of(
                Resource.builder()
                        .name("Conference Room A")
                        .description("Seats 12, whiteboard, video screen")
                        .type("ROOM")
                        .available(true)
                        .price(new BigDecimal("1500.00"))
                        .build(),
                Resource.builder()
                        .name("Toyota Camry")
                        .description("Sedan, automatic, GPS included")
                        .type("VEHICLE")
                        .available(true)
                        .price(new BigDecimal("2500.00"))
                        .build(),
                Resource.builder()
                        .name("Projector X200")
                        .description("4K portable projector with HDMI")
                        .type("EQUIPMENT")
                        .available(true)
                        .price(new BigDecimal("500.00"))
                        .build(),
                Resource.builder()
                        .name("Meeting Room B")
                        .description("Seats 6, coffee machine")
                        .type("ROOM")
                        .available(false)   // ← intentionally unavailable, for testing later
                        .price(new BigDecimal("800.00"))
                        .build()
        );

        resourceRepository.saveAll(resources);
        log.info("Seeded {} resources", resources.size());
    }
}