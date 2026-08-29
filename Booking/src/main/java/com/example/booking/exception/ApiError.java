package com.example.booking.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

/** Uniform JSON error body for the entire API */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)   // hide null fields (e.g. fieldErrors)
public class ApiError {
    private final int status;
    private final String error;
    private final String message;
    private final String path;
    private final LocalDateTime timestamp;
    private final Map<String, String> fieldErrors;   // only for validation errors
}