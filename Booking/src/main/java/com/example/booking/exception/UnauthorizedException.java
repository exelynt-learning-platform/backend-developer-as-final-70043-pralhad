package com.example.booking.exception;

/** Authenticated but not allowed — e.g., USER opening another user's reservation */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}