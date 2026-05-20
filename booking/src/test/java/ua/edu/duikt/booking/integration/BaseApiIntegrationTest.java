package ua.edu.duikt.booking.integration;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.entity.Equipment;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;
import ua.edu.duikt.booking.repository.BuildingRepository;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ClassroomTypeRepository;
import ua.edu.duikt.booking.repository.EquipmentRepository;
import ua.edu.duikt.booking.repository.ReservationRepository;
import ua.edu.duikt.booking.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseApiIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected BuildingRepository buildingRepository;

    @Autowired
    protected ClassroomTypeRepository classroomTypeRepository;

    @Autowired
    protected ClassroomRepository classroomRepository;

    @Autowired
    protected EquipmentRepository equipmentRepository;

    @Autowired
    protected ReservationRepository reservationRepository;

    protected final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    protected User persistUser(String email, UserRole role, UserStatus status) {
        String normalizedEmail = normalizeEmail(email);

        return userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .firstName("Test")
                                .lastName("User")
                                .email(normalizedEmail)
                                .passwordHash("hash")
                                .role(role)
                                .status(status)
                                .createdAt(LocalDateTime.now())
                                .build()
                ));
    }

    protected Building persistBuilding(String name) {
        String normalizedName = normalizeRequired(name);

        return buildingRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> buildingRepository.save(
                        Building.builder()
                                .name(normalizedName)
                                .address("вул. Тестова, 1")
                                .build()
                ));
    }

    protected ClassroomType persistClassroomType(String name) {
        String normalizedName = normalizeRequired(name);

        return classroomTypeRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> classroomTypeRepository.save(
                        ClassroomType.builder()
                                .name(normalizedName)
                                .description("Тестовий тип аудиторії")
                                .build()
                ));
    }

    protected Equipment persistEquipment(String name) {
        String normalizedName = normalizeRequired(name);

        return equipmentRepository.findByNameIgnoreCase(normalizedName)
                .orElseGet(() -> equipmentRepository.save(
                        Equipment.builder()
                                .name(normalizedName)
                                .description("Тестове обладнання")
                                .build()
                ));
    }

    protected Classroom persistClassroom(String name, boolean isActive) {
        return persistClassroom(
                name,
                isActive,
                persistBuilding("Корпус 1"),
                persistClassroomType("Лекційна")
        );
    }

    protected Classroom persistClassroom(String name,
                                         boolean isActive,
                                         Building building,
                                         ClassroomType classroomType) {
        return classroomRepository.save(
                Classroom.builder()
                        .name(normalizeRequired(name))
                        .building(building)
                        .classroomType(classroomType)
                        .capacity(30)
                        .floor(1)
                        .description("Тестова аудиторія")
                        .isActive(isActive)
                        .build()
        );
    }

    protected Reservation persistReservation(User user,
                                             Classroom classroom,
                                             LocalDate reservationDate,
                                             LocalTime startTime,
                                             LocalTime endTime,
                                             String purpose,
                                             ReservationStatus status) {
        return reservationRepository.save(
                Reservation.builder()
                        .user(user)
                        .classroom(classroom)
                        .reservationDate(reservationDate)
                        .startTime(startTime)
                        .endTime(endTime)
                        .purpose(purpose)
                        .status(status)
                        .createdAt(LocalDateTime.now())
                        .build()
        );
    }

    protected SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor userJwt(String email, String role) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = role == null ? "USER" : role.trim().toUpperCase(Locale.ROOT);

        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(jwt -> jwt
                        .claim("email", normalizedEmail)
                        .claim("preferred_username", normalizedEmail)
                        .claim("realm_access", Map.of("roles", List.of(normalizedRole)))
                        .subject(normalizedEmail)
                )
                .authorities(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}