package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.ReservationRequestDto;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.service.ReservationService;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "Reservations", description = "Операції для роботи з бронюваннями")
public class ReservationController {

    private final ReservationService reservationService;

    @GetMapping
    @Operation(summary = "Отримати список усіх бронювань")
    public List<Reservation> getAllReservations() {
        return reservationService.getAllReservations();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати бронювання за id")
    public Reservation getReservationById(@PathVariable Long id) {
        return reservationService.getReservationById(id);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Отримати бронювання конкретного користувача")
    public List<Reservation> getUserReservations(@PathVariable Long userId) {
        return reservationService.getUserReservations(userId);
    }

    @GetMapping("/classroom/{classroomId}")
    @Operation(summary = "Отримати бронювання конкретної аудиторії")
    public List<Reservation> getClassroomReservations(@PathVariable Long classroomId) {
        return reservationService.getClassroomReservations(classroomId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити нове бронювання")
    public Reservation createReservation(@RequestBody ReservationRequestDto request,
                                         @AuthenticationPrincipal Jwt jwt) {
        String email = resolveEmail(jwt);
        return reservationService.createReservation(request, email);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалити бронювання")
    public ResponseEntity<Void> deleteReservation(@PathVariable Long id) {
        reservationService.deleteReservation(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveEmail(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("preferred_username");
        }
        return email;
    }
}
