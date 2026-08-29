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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // READ — ADMIN sees all, USER sees only their own ⭐
    // ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getReservations(String username,
                                                             ReservationStatus status, BigDecimal minPrice, BigDecimal maxPrice,
                                                             Pageable pageable) {

        Specification<Reservation> spec =
                buildSpec(username, status, minPrice, maxPrice);

        Page<Reservation> page = reservationRepository.findAll(spec, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(Long id, String username) {
        Reservation r = findReservation(id);
        assertOwnershipOrAdmin(r, username);
        return toResponse(r);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE — owner ALWAYS comes from JWT ⭐⭐ SECURITY
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public ReservationResponse createReservation(ReservationRequest request,
                                                 String username) {

        validateTimes(request.getStartTime(), request.getEndTime());

        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ConflictException("startTime cannot be in the past");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Resource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resource not found with id: " + request.getResourceId()));

        if (!resource.isAvailable()) {
            throw new ConflictException("Resource is not available for booking");
        }

        // Double-booking prevention ⭐
        if (reservationRepository.countOverlapping(
                resource.getId(), request.getStartTime(), request.getEndTime()) > 0) {
            throw new ConflictException(
                    "Resource already has a reservation in this time slot");
        }

        Reservation reservation = Reservation.builder()
                .user(user)                              // ← from JWT, never from client
                .resource(resource)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .price(resource.getPrice())              // ← snapshot at booking time
                .status(ReservationStatus.PENDING)       // ← always PENDING on create
                .build();

        Reservation saved = reservationRepository.save(reservation);
        log.info("Reservation {} created for '{}' on resource '{}'",
                saved.getId(), username, resource.getName());
        return toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE / DELETE — ADMIN only (enforced by SecurityConfig)
    // ─────────────────────────────────────────────────────────────
    @Transactional
    public ReservationResponse updateReservation(Long id, ReservationRequest request) {
        Reservation reservation = findReservation(id);

        Resource resource = resourceRepository.findById(request.getResourceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resource not found with id: " + request.getResourceId()));

        validateTimes(request.getStartTime(), request.getEndTime());

        boolean timesChanged =
                !reservation.getStartTime().equals(request.getStartTime())
                        || !reservation.getEndTime().equals(request.getEndTime());

        if (timesChanged
                && reservationRepository.countOverlappingExcluding(
                resource.getId(), id,
                request.getStartTime(), request.getEndTime()) > 0) {
            throw new ConflictException(
                    "Requested time slot conflicts with another reservation");
        }

        reservation.setResource(resource);
        reservation.setStartTime(request.getStartTime());
        reservation.setEndTime(request.getEndTime());

        if (request.getStatus() != null) {   // ADMIN may change status
            reservation.setStatus(request.getStatus());
        }

        return toResponse(reservation);      // dirty checking persists
    }

    @Transactional
    public void deleteReservation(Long id) {
        reservationRepository.delete(findReservation(id));
    }

    // ─────────────────────────────────────────────────────────────
    // helpers
    // ─────────────────────────────────────────────────────────────
    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (!start.isBefore(end)) {
            throw new ConflictException("startTime must be before endTime");
        }
    }

    private Reservation findReservation(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with id: " + id));
    }

    /** USER → only own; ADMIN → everything */
    private void assertOwnershipOrAdmin(Reservation reservation, String username) {
        boolean isAdmin = userRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);

        if (!isAdmin && !reservation.getUser().getUsername().equals(username)) {
            throw new UnauthorizedException(
                    "You can only access your own reservations");
        }
    }

    /** Dynamically builds the WHERE clause — filters only if present ⭐ */
    private Specification<Reservation> buildSpec(String username,
                                                 ReservationStatus status, BigDecimal minPrice, BigDecimal maxPrice) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));

        List<Specification<Reservation>> specs = new ArrayList<>();

        if (user.getRole() != Role.ADMIN) {
            // USER → ownership filter is ALWAYS applied. Non-negotiable. 🔒
            specs.add(ReservationSpecification.hasUserId(user.getId()));
        }
        if (status != null) {
            specs.add(ReservationSpecification.hasStatus(status));
        }
        if (minPrice != null) {
            specs.add(ReservationSpecification.priceGreaterThanOrEqual(minPrice));
        }
        if (maxPrice != null) {
            specs.add(ReservationSpecification.priceLessThanOrEqual(maxPrice));
        }

        // ANDs everything together; empty list → match all (ADMIN, no filters)
        return Specification.allOf(specs);
    }

    private ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .username(r.getUser().getUsername())
                .resourceId(r.getResource().getId())
                .resourceName(r.getResource().getName())
                .startTime(r.getStartTime())
                .endTime(r.getEndTime())
                .price(r.getPrice())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}