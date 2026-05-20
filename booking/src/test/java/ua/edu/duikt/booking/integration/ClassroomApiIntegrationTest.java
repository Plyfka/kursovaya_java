package ua.edu.duikt.booking.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import ua.edu.duikt.booking.dto.ClassroomRequestDto;
import ua.edu.duikt.booking.entity.Classroom;
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

class ClassroomApiIntegrationTest extends BaseApiIntegrationTest {

    @Test
    void ts03_shouldReturnActiveClassroomsForAuthorizedUser() throws Exception {
        persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        persistClassroom("101", true);
        persistClassroom("102", false);

        mockMvc.perform(get("/api/classrooms/active")
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("101"));
    }

    @Test
    void ts04_shouldReturnClassroomDetails() throws Exception {
        persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("101", true);

        mockMvc.perform(get("/api/classrooms/{id}", classroom.getId())
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("101"))
                .andExpect(jsonPath("$.buildingName").value("Корпус 1"))
                .andExpect(jsonPath("$.classroomTypeName").value("Лекційна"));
    }

    @Test
    void ts05_shouldReturnAvailableClassrooms() throws Exception {
        User user = persistUser("user@university.local", UserRole.USER, UserStatus.ACTIVE);
        Classroom available = persistClassroom("101", true);
        Classroom busy = persistClassroom("102", true);
        persistReservation(user, busy,
                LocalDate.of(2026, 6, 10),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара",
                ReservationStatus.CREATED);

        mockMvc.perform(get("/api/classrooms/available")
                        .param("date", "2026-06-10")
                        .param("startTime", "10:00:00")
                        .param("endTime", "11:00:00")
                        .with(userJwt("user@university.local", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(available.getId()));
    }

    @Test
    void ts12_shouldCreateClassroomAsAdmin() throws Exception {
        persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);
        var building = persistBuilding("Корпус 1");
        var classroomType = persistClassroomType("Лекційна");
        ClassroomRequestDto request = new ClassroomRequestDto(
                "201",
                building.getId(),
                classroomType.getId(),
                40,
                2,
                "Нова аудиторія",
                true
        );

        mockMvc.perform(post("/api/classrooms")
                        .with(userJwt("admin@university.local", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("201"))
                .andExpect(jsonPath("$.capacity").value(40));
    }

    @Test
    void ts13_shouldDeleteClassroomWhenItHasNoReservations() throws Exception {
        persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("301", true);

        mockMvc.perform(delete("/api/classrooms/{id}", classroom.getId())
                        .with(userJwt("admin@university.local", "ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void ts14_shouldNotDeleteClassroomWhenReservationsExist() throws Exception {
        User admin = persistUser("admin@university.local", UserRole.ADMIN, UserStatus.ACTIVE);
        Classroom classroom = persistClassroom("302", true);
        persistReservation(admin, classroom,
                LocalDate.of(2026, 6, 11),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "Нарада",
                ReservationStatus.CREATED);

        mockMvc.perform(delete("/api/classrooms/{id}", classroom.getId())
                        .with(userJwt("admin@university.local", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void ts17_shouldReturnForbiddenForBlockedUser() throws Exception {
        persistUser("blocked@university.local", UserRole.USER, UserStatus.BLOCKED);
        persistClassroom("401", true);

        mockMvc.perform(get("/api/classrooms/active")
                        .with(userJwt("blocked@university.local", "USER")))
                .andExpect(status().isForbidden());
    }
}
