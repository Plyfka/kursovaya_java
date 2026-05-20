package ua.edu.duikt.booking.dto;

import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;

public record UserRequestDto(
        String firstName,
        String lastName,
        String email,
        String password,
        UserRole role,
        UserStatus status
) {
}
