package com.example.booking.service;

import com.example.booking.dto.PageResponse;
import com.example.booking.dto.ReservationRequest;
import com.example.booking.dto.ReservationResponse;
import com.example.booking.entity.*;
import com.example.booking.exception.ConflictException;
import com.example.booking.exception.ResourceNotFoundException;
import com.example.booking.exception.UnauthorizedException;
import com.example.booking.repository.ReservationRepository;
import com.example.booking.repository.ResourceRepository;
import com.example.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReservationService business rules.
 * Repositories are Mockito mocks — no Spring context, no database.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private ResourceRepository resourceRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReservationService reservationService;

    private User john;
    private User jane;
    private User admin;
    private Resource roomA;    // available, 1500.00
    private Resource roomB;    // available, 800.00 (for resource-change tests)

    @BeforeEach
    void setUp() {
        john  = User.builder().id(1L).username("john").password("x").role(Role.USER).build();
        jane  = User.builder().id(2L).username("jane").password("x").role(Role.USER).build();
        admin = User.builder().id(3L).username("admin").password("x").role(Role.ADMIN).build();
        roomA = Resource.builder().id(10L).name("Conference Room A").type("ROOM")
                .available(true).price(new BigDecimal("1500.00")).build();
        roomB = Resource.builder().id(12L).name("Meeting Room B").type("ROOM")
                .available(true).price(new BigDecimal("800.00")).build();
    }

    private ReservationRequest validRequest() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return ReservationRequest.builder()
                .resourceId(10L)
                .startTime(start)
                .endTime(start.plusHours(2))
                .build();
    }

    private Reservation johnsReservation() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return Reservation.builder().id(5L).user(john).resource(roomA)
                .startTime(start).endTime(start.plusHours(2))
                .price(new BigDecimal("1500.00"))
                .status(ReservationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─── CREATE ────────────────────────────────────────────────

    @Test
    @DisplayName("Create: owner from JWT, price snapshot, status forced PENDING")
    void createReservation_success_ownerFromJwtAndForcedPending() {
        ReservationRequest request = validRequest();
        request.setStatus(ReservationStatus.CONFIRMED); // client tries to self-confirm!

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(roomA));
        when(reservationRepository.countOverlapping(10L,
                request.getStartTime(), request.getEndTime())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        ReservationResponse response =
                reservationService.createReservation(request, "john");

        assertThat(response.getUsername()).isEqualTo("john");
        assertThat(response.getPrice()).isEqualByComparingTo("1500.00");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING); // forced!
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("Create: overlapping time slot rejected (409)")
    void createReservation_rejectsOverlappingSlot() {
        ReservationRequest request = validRequest();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(roomA));
        when(reservationRepository.countOverlapping(10L,
                request.getStartTime(), request.getEndTime())).thenReturn(1L);

        assertThatThrownBy(() -> reservationService.createReservation(request, "john"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("time slot");
    }

    @Test
    @DisplayName("Create: unavailable resource rejected")
    void createReservation_rejectsUnavailableResource() {
        Resource unavailable = Resource.builder().id(11L).name("Locked Room").type("ROOM")
                .available(false).price(new BigDecimal("100.00")).build();

        ReservationRequest request = validRequest();
        request.setResourceId(11L);

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));
        when(resourceRepository.findById(11L)).thenReturn(Optional.of(unavailable));

        assertThatThrownBy(() -> reservationService.createReservation(request, "john"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("Create: startTime must be before endTime")
    void createReservation_rejectsStartAfterEnd() {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        ReservationRequest request = ReservationRequest.builder()
                .resourceId(10L).startTime(start).endTime(start.minusHours(1)).build();

        assertThatThrownBy(() -> reservationService.createReservation(request, "john"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("must be before");
    }

    @Test
    @DisplayName("Create: startTime in the past rejected")
    void createReservation_rejectsPastStartTime() {
        LocalDateTime start = LocalDateTime.now().minusDays(5);
        ReservationRequest request = ReservationRequest.builder()
                .resourceId(10L).startTime(start).endTime(start.plusHours(2)).build();

        assertThatThrownBy(() -> reservationService.createReservation(request, "john"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("past");
    }

    @Test
    @DisplayName("Create: unknown resourceId → 404")
    void createReservation_rejectsUnknownResource() {
        ReservationRequest request = validRequest();
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));
        when(resourceRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(request, "john"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Resource not found");
    }

    // ─── OWNERSHIP (criterion #5) ──────────────────────────────

    @Test
    @DisplayName("Ownership: owner can view their own reservation")
    void getReservationById_ownerCanViewOwn() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(johnsReservation()));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));

        ReservationResponse response =
                reservationService.getReservationById(5L, "john");

        assertThat(response.getUsername()).isEqualTo("john");
    }

    @Test
    @DisplayName("Ownership: ADMIN can view anyone's reservation")
    void getReservationById_adminCanViewAny() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(johnsReservation()));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        ReservationResponse response =
                reservationService.getReservationById(5L, "admin");

        assertThat(response.getUsername()).isEqualTo("john");
    }

    @Test
    @DisplayName("Ownership: another USER blocked → UnauthorizedException (403) 🔒")
    void getReservationById_otherUserForbidden() {
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(johnsReservation()));
        when(userRepository.findByUsername("jane")).thenReturn(Optional.of(jane));

        assertThatThrownBy(() -> reservationService.getReservationById(5L, "jane"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("own reservations");
    }

    @Test
    @DisplayName("Get by id: unknown id → 404")
    void getReservationById_notFound() {
        when(reservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.getReservationById(99L, "john"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── LIST ──────────────────────────────────────────────────

    @Test
    @DisplayName("List: reservations mapped into PageResponse")
    void getReservations_returnsMappedPage() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(john));
        when(reservationRepository.findAll(
                ArgumentMatchers.<Specification<Reservation>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(johnsReservation()),
                        PageRequest.of(0, 10), 1));

        PageResponse<ReservationResponse> result = reservationService.getReservations(
                "john", null, null, null, PageRequest.of(0, 10));

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).getUsername()).isEqualTo("john");
    }

    // ─── UPDATE — including REVIEW FIX tests ⭐ ────────────────

    @Test
    @DisplayName("Update: ADMIN can confirm a PENDING reservation")
    void updateReservation_adminCanConfirm() {
        Reservation reservation = johnsReservation();
        ReservationRequest request = ReservationRequest.builder()
                .resourceId(10L)
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .status(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(resourceRepository.findById(10L)).thenReturn(Optional.of(roomA));

        ReservationResponse response =
                reservationService.updateReservation(5L, request);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: update to UNAVAILABLE resource rejected (409)")
    void updateReservation_rejectsUnavailableResource() {
        Resource unavailable = Resource.builder().id(11L).name("Locked Room").type("ROOM")
                .available(false).price(new BigDecimal("100.00")).build();

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        ReservationRequest request = ReservationRequest.builder()
                .resourceId(11L)                       // admin moves to unavailable room
                .startTime(start).endTime(start.plusHours(2))
                .build();

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(johnsReservation()));
        when(resourceRepository.findById(11L)).thenReturn(Optional.of(unavailable));

        assertThatThrownBy(() -> reservationService.updateReservation(5L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("⭐ REVIEW FIX: moving reservation to another resource re-checks overlap")
    void updateReservation_rejectsOverlapWhenResourceChanges() {
        Reservation reservation = johnsReservation(); // on roomA, tomorrow 10:00-12:00

        ReservationRequest request = ReservationRequest.builder()
                .resourceId(12L)                       // switch to roomB…
                .startTime(reservation.getStartTime()) // …same times
                .endTime(reservation.getEndTime())
                .build();

        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(resourceRepository.findById(12L)).thenReturn(Optional.of(roomB));
        // roomB's slot is already taken by another reservation
        when(reservationRepository.countOverlappingExcluding(12L, 5L,
                request.getStartTime(), request.getEndTime())).thenReturn(1L);

        assertThatThrownBy(() -> reservationService.updateReservation(5L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("conflicts with another reservation");
    }
}