package ua.edu.duikt.booking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ReservationResponseDto(
        Long id,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        String purpose,
        String status,
        LocalDateTime createdAt,
        Long classroomId,
        String classroomName
) {
}