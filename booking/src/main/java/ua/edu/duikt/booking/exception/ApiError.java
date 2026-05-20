package ua.edu.duikt.booking.exception;

import java.time.LocalDateTime;

public record ApiError(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {
}
