package com.example.booking.exception;

/** Business-rule conflict — e.g., double-booking a time slot */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}