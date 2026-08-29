package com.example.booking.controller;

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
 * Integration tests for filtering (criterion #7), pagination & sorting
 * (criterion #8), double-booking (409), status transitions, and the
 * review-fix behaviors — all through real HTTP.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReservationApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private String johnToken;
    private String adminToken;
    private Long roomId;       // 1000.00, available
    private Long projectorId;  // 500.00, available
    private Long unavailableId;// 800.00, UNAVAILABLE

    @BeforeEach
    void setUp() throws Exception {
        reservationRepository.deleteAll();   // reservations FIRST — FK constraints
        userRepository.deleteAll();
        resourceRepository.deleteAll();

        userRepository.save(User.builder()
                .username("admin").password(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN).build());
        userRepository.save(User.builder()
                .username("john").password(passwordEncoder.encode("john123"))
                .role(Role.USER).build());

        roomId = resourceRepository.save(Resource.builder()
                .name("Conference Room A").description("d").type("ROOM")
                .available(true).price(new BigDecimal("1000.00")).build()).getId();

        projectorId = resourceRepository.save(Resource.builder()
                .name("Projector X200").description("d").type("EQUIPMENT")
                .available(true).price(new BigDecimal("500.00")).build()).getId();

        unavailableId = resourceRepository.save(Resource.builder()
                .name("Meeting Room B").description("d").type("ROOM")
                .available(false).price(new BigDecimal("800.00")).build()).getId();

        johnToken = login("john", "john123");
        adminToken = login("admin", "admin123");
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

    private Long bookAsJohn(Long resourceId, String start, String end) throws Exception {
        String location = mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"%s","endTime":"%s"}"""
                                .formatted(resourceId, start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    // ─── FILTERING (criterion #7) ⭐ ───────────────────────────

    @Test
    @DisplayName("Filter by status=CONFIRMED returns only confirmed")
    void filterByStatus() throws Exception {
        Long r1 = bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");
        bookAsJohn(projectorId, "2026-12-02T10:00:00", "2026-12-02T11:00:00");

        mockMvc.perform(put("/reservations/" + r1)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00","status":"CONFIRMED"}"""
                                .formatted(roomId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/reservations")
                        .param("status", "CONFIRMED")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("Filter by minPrice/maxPrice bounds")
    void filterByPriceRange() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");        // 1000
        bookAsJohn(projectorId, "2026-12-02T10:00:00", "2026-12-02T11:00:00"); // 500

        mockMvc.perform(get("/reservations")
                        .param("minPrice", "800")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(1000.00));

        mockMvc.perform(get("/reservations")
                        .param("maxPrice", "600")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value(500.00));

        mockMvc.perform(get("/reservations")
                        .param("minPrice", "400").param("maxPrice", "800")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Invalid enum value ?status=MAYBE → 400")
    void invalidStatusRejected() throws Exception {
        mockMvc.perform(get("/reservations")
                        .param("status", "MAYBE")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isBadRequest());
    }

    // ─── PAGINATION & SORTING (criterion #8) ⭐ ────────────────

    @Test
    @DisplayName("Pagination: page=0&size=1 → 1 item, 2 total, 2 pages")
    void paginationWorks() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");
        bookAsJohn(projectorId, "2026-12-02T10:00:00", "2026-12-02T11:00:00");

        mockMvc.perform(get("/reservations")
                        .param("page", "0").param("size", "1")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: page size capped at 100 (?size=5000)")
    void pageSizeCappedAtMax() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(get("/reservations")
                        .param("size", "5000")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));   // capped, not 5000!
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: negative page clamped to 0 (?page=-5)")
    void negativePageClampedToZero() throws Exception {
        mockMvc.perform(get("/reservations")
                        .param("page", "-5")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    @DisplayName("Sorting: sort=price,desc → highest price first")
    void sortingByPriceDesc() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");        // 1000
        bookAsJohn(projectorId, "2026-12-02T10:00:00", "2026-12-02T11:00:00"); // 500

        mockMvc.perform(get("/reservations")
                        .param("sort", "price,desc")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].price").value(1000.00))
                .andExpect(jsonPath("$.content[1].price").value(500.00));
    }

    @Test
    @DisplayName("Invalid sort field → 400 (whitelist protection)")
    void invalidSortFieldRejected() throws Exception {
        mockMvc.perform(get("/reservations")
                        .param("sort", "password,desc")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: malformed sort 'price,desc,extra' → 400")
    void malformedSortRejected() throws Exception {
        mockMvc.perform(get("/reservations")
                        .param("sort", "price,desc,extra")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isBadRequest());
    }

    // ─── BUSINESS RULES ────────────────────────────────────────

    @Test
    @DisplayName("Double-booking same slot → 409 Conflict")
    void overlappingBookingRejected() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,
                                 "startTime":"2026-12-01T11:00:00",
                                 "endTime":"2026-12-01T13:00:00"}"""
                                .formatted(roomId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Resource already has a reservation in this time slot"));
    }

    @Test
    @DisplayName("Same room, DIFFERENT time → 201 (no false conflict)")
    void nonOverlappingBookingSucceeds() throws Exception {
        bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,
                                 "startTime":"2026-12-01T14:00:00",
                                 "endTime":"2026-12-01T16:00:00"}"""
                                .formatted(roomId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ADMIN confirms PENDING → CONFIRMED")
    void adminConfirmsReservation() throws Exception {
        Long id = bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(put("/reservations/" + id)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00","status":"CONFIRMED"}"""
                                .formatted(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("ADMIN cancels → CANCELLED, slot becomes bookable again")
    void cancelledSlotFreedForRebooking() throws Exception {
        Long id = bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(put("/reservations/" + id)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00","status":"CANCELLED"}"""
                                .formatted(roomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/reservations")
                        .header("Authorization", auth(johnToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,
                                 "startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""
                                .formatted(roomId)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: admin cannot move reservation to UNAVAILABLE resource")
    void updateToUnavailableResourceRejected() throws Exception {
        Long id = bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        mockMvc.perform(put("/reservations/" + id)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""
                                .formatted(unavailableId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Resource is not available for booking"));
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: switching resource re-checks overlap on new resource")
    void updateRejectsOverlapWhenResourceChanges() throws Exception {
        // r1 on room (Dec 1, 10-12)
        Long r1 = bookAsJohn(roomId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");
        // r2 on projector, SAME times (allowed — different resource)
        bookAsJohn(projectorId, "2026-12-01T10:00:00", "2026-12-01T12:00:00");

        // admin moves r1 onto the projector — same times, different resource
        // → must detect conflict with r2 → 409
        mockMvc.perform(put("/reservations/" + r1)
                        .header("Authorization", auth(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceId":%d,"startTime":"2026-12-01T10:00:00",
                                 "endTime":"2026-12-01T12:00:00"}"""
                                .formatted(projectorId)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET unknown reservation → 404 with clean JSON")
    void unknownReservationReturns404() throws Exception {
        mockMvc.perform(get("/reservations/9999")
                        .header("Authorization", auth(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Reservation not found with id: 9999"));
    }
}