package ua.edu.duikt.booking.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationRequestDto(
        Long classroomId,
        LocalDate reservationDate,
        LocalTime startTime,
        LocalTime endTime,
        String purpose
) {
}
