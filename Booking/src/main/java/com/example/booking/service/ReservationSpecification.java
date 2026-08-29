package com.example.booking.service;

import com.example.booking.entity.Reservation;
import com.example.booking.entity.ReservationStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/** Reusable building blocks for dynamic WHERE clauses. */
public final class ReservationSpecification {

    private ReservationSpecification() {}   // static-only utility class

    public static Specification<Reservation> hasUserId(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<Reservation> hasStatus(ReservationStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Reservation> priceGreaterThanOrEqual(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Reservation> priceLessThanOrEqual(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }
}