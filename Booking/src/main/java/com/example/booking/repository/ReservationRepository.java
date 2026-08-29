package com.example.booking.repository;


import com.example.booking.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    /**
     * Overlap check for CREATE:
     * Two time ranges overlap when existingStart < newEnd AND existingEnd > newStart.
     * CANCELLED reservations are ignored — their slot is free again.
     */
    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status <> com.example.booking.entity.ReservationStatus.CANCELLED
              AND r.startTime < :endTime
              AND r.endTime > :startTime
            """)
    long countOverlapping(@Param("resourceId") Long resourceId,
                          @Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime);

    /**
     * Overlap check for UPDATE:
     * Same logic, but excludes the reservation being edited
     * (otherwise a reservation would collide with itself).
     */
    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.resource.id = :resourceId
              AND r.status <> com.example.booking.entity.ReservationStatus.CANCELLED
              AND r.id <> :reservationId
              AND r.startTime < :endTime
              AND r.endTime > :startTime
            """)
    long countOverlappingExcluding(@Param("resourceId") Long resourceId,
                                   @Param("reservationId") Long reservationId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
}