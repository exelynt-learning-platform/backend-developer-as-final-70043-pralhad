package com.example.booking.security;

import com.example.booking.entity.Resource;
import com.example.booking.entity.Role;
import com.example.booking.entity.User;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests through the REAL security chain:
 * HTTP → JwtAuthenticationFilter → SecurityConfig rules → controller.
 * H2 in-memory DB. No real MySQL touched.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedData() {
        reservationRepository.deleteAll();   // reservations FIRST — FK constraints
        userRepository.deleteAll();
        resourceRepository.deleteAll();

        userRepository.save(User.builder()
                .username("admin").password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN).build());
        userRepository.save(User.builder()
                .username("john").password(passwordEncoder.encode("john123"))
                .role(Role.USER).build());
        userRepository.save(User.builder()
                .username("jane").password(passwordEncoder.encode("jane123"))
                .role(Role.USER).build());

        resourceRepository.save(Resource.builder()
                .name("Conference Room A").description("test").type("ROOM")
                .available(true).price(new BigDecimal("1500.00")).build());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}"""
                                .formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asText();
    }

    private String auth(String token) { return "Bearer " + token; }

    // ─── AUTHENTICATION (criterion #1) ─────────────────────────

    @Test
    @DisplayName("GET /resources without token → 401")
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/resources"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(
                        "Authentication required or token invalid/expired"));
    }

    @Test
    @DisplayName("GET /resources with garbage token → 401")
    void protectedEndpoint_garbageToken_returns401() throws Exception {
        mockMvc.perform(get("/resources")
                        .header("Authorization", "Bearer fake.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Register: new user always gets USER role (no privilege escalation)")
    void register_alwaysAssignsUserRole() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"hacker","password":"hack123","role":"ADMIN"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));   // ADMIN ignored!
    }

    // ─── RBAC (criterion #2) ───────────────────────────────────

    @Test
    @DisplayName("USER POST /resources → 403 Forbidden")
    void userCannotCreateResource() throws Exception {
        String token = login("john", "john123");

        mockMvc.perform(post("/resources")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Hack Room","type":"ROOM",
                                 "available":true,"price":100.00}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value(
                        "You do not have permission to perform this action"));
    }

    @Test
    @DisplayName("USER DELETE /resources/1 → 403 Forbidden")
    void userCannotDeleteResource() throws Exception {
        String token = login("john", "john123");

        mockMvc.perform(delete("/resources/1")
                        .header("Authorization", auth(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("USER PUT /reservations/1 → 403 Forbidden")
    void userCannotUpdateReservation() throws Exception {
        String token = login("john", "john123");

        mockMvc.perform(put("/reservations/1")
                        .header("Authorization", auth(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""))
                .andExpect(status().isForbidden());
    }

    // ─── OWNERSHIP (criterion #5) — the crown jewels 🔒 ────────

    @Test
    @DisplayName("USER list /reservations → sees ONLY own rows")
    void userSeesOnlyOwnReservations() throws Exception {
        String johnToken = login("john", "john123");

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""))
                .andExpect(status().isCreated());

        String janeToken = login("jane", "jane123");
        mockMvc.perform(get("/reservations")
                        .header("Authorization", auth(janeToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));  // 🔒 empty!
    }

    @Test
    @DisplayName("USER GET /reservations/{id} of another user → 403")
    void userCannotViewOthersReservationById() throws Exception {
        String johnToken = login("john", "john123");

        String location = mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String reservationId = location.substring(location.lastIndexOf('/') + 1);

        String janeToken = login("jane", "jane123");
        mockMvc.perform(get("/reservations/" + reservationId)
                        .header("Authorization", auth(janeToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You can only access your own reservations"));
    }

    @Test
    @DisplayName("userId in reservation body IGNORED — owner is the JWT user 🔒")
    void userIdInBodyIsIgnored() throws Exception {
        String johnToken = login("john", "john123");

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,"userId":999,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("john"))   // NOT 999!
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("ADMIN GET /reservations → sees ALL reservations")
    void adminSeesAllReservations() throws Exception {
        String johnToken = login("john", "john123");
        String janeToken = login("jane", "jane123");

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(janeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":1,
                                 "startTime":"2026-12-01T14:00:00",
                                 "endTime":"2026-12-01T16:00:00"}"""))
                .andExpect(status().isCreated());

        String adminToken = login("admin", "admin123");
        mockMvc.perform(get("/reservations")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));  // both!
    }

    // ─── HAPPY PATH + VALIDATION ───────────────────────────────

    @Test
    @DisplayName("USER GET /resources with valid token → 200 + list")
    void userCanReadResources() throws Exception {
        String token = login("john", "john123");

        mockMvc.perform(get("/resources")
                        .header("Authorization", auth(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Conference Room A"));
    }
}