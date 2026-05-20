package ua.edu.duikt.booking.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ua.edu.duikt.booking.dto.ReservationRequestDto;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReservationApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void ts06_shouldCreateReservationWithoutConflict() throws Exception {
        persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        ReservationRequestDto request = new ReservationRequestDto(
                classroom.getId(),
                LocalDate.of(2026, 6, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );

        mockMvc.perform(post("/api/reservations")
                        .with(userJwt("user@university.local", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.purpose").value("Пара"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void ts07_shouldRejectReservationWithConflict() throws Exception {
        User user = persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        persistReservation(user, classroom,
                LocalDate.of(2026, 6, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Існуюче бронювання",
                ReservationStatus.CREATED);

        ReservationRequestDto request = new ReservationRequestDto(
                classroom.getId(),
                LocalDate.of(2026, 6, 20),
                LocalTime.of(10, 30),
                LocalTime.of(11, 30),
                "Нове бронювання"
        );

        mockMvc.perform(post("/api/reservations")
                        .with(userJwt("user@university.local", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void ts08_shouldRejectReservationWithInvalidTimeInterval() throws Exception {
        persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        ReservationRequestDto request = new ReservationRequestDto(
                classroom.getId(),
                LocalDate.of(2026, 6, 20),
                LocalTime.of(12, 0),
                LocalTime.of(11, 0),
                "Некоректний час"
        );

        mockMvc.perform(post("/api/reservations")
                        .with(userJwt("user@university.local", "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ts09_shouldReturnOwnReservations() throws Exception {
        User owner = persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        User other = persistUser("other@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        persistReservation(owner, classroom,
                LocalDate.of(2026, 6, 21),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Моє бронювання",
                ReservationStatus.CREATED);
        persistReservation(other, classroom,
                LocalDate.of(2026, 6, 22),
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "Чуже бронювання",
                ReservationStatus.CREATED);

        mockMvc.perform(get("/api/me/reservations")
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].purpose").value("Моє бронювання"));
    }

    @Test
    void ts10_shouldDeleteOwnReservation() throws Exception {
        User owner = persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        Reservation reservation = persistReservation(owner, classroom,
                LocalDate.of(2026, 6, 23),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Видалити",
                ReservationStatus.CREATED);

        mockMvc.perform(delete("/api/me/reservations/{id}", reservation.getId())
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isOk());
    }

    @Test
    void ts11_shouldRejectDeletingOtherUsersReservation() throws Exception {
        User owner = persistUser("owner@university.local", UserRole.USER, UserStatus.ACTIVE);
        persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        Reservation reservation = persistReservation(owner, classroom,
                LocalDate.of(2026, 6, 24),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Чуже бронювання",
                ReservationStatus.CREATED);

        mockMvc.perform(delete("/api/me/reservations/{id}", reservation.getId())
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void ts17_shouldReturnAllReservationsForAdmin() throws Exception {
        User admin = persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);
        persistReservation(admin, classroom,
                LocalDate.of(2026, 6, 25),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "Адмін бронювання",
                ReservationStatus.CREATED);

        mockMvc.perform(get("/api/reservations")
                        .with(userJwt("admin@university.local", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
