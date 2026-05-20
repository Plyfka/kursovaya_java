package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.ReservationResponseDto;
import ua.edu.duikt.booking.dto.UserMeDto;
import ua.edu.duikt.booking.service.ReservationService;
import ua.edu.duikt.booking.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Операції для поточного авторизованого користувача")
public class MeController {

    private final UserService userService;
    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "Отримати інформацію про поточного користувача")
    public UserMeDto getMe(@AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        return userService.getCurrentUserInfo(email);
    }

    @GetMapping("/reservations")
    @Operation(summary = "Отримати бронювання поточного користувача")
    public List<ReservationResponseDto> getMyReservations(@AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        return reservationService.getMyReservations(email);
    }

    @DeleteMapping("/reservations/{id}")
    @Operation(summary = "Видалити власне бронювання")
    public void deleteMyReservation(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        reservationService.deleteMyReservation(id, email);
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }
}