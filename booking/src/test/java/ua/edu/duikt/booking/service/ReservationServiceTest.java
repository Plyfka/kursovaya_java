package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.duikt.booking.dto.ReservationRequestDto;
import ua.edu.duikt.booking.dto.ReservationResponseDto;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.entity.UserRole;
import ua.edu.duikt.booking.entity.UserStatus;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ReservationRepository;
import ua.edu.duikt.booking.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ReservationService reservationService;

    @Test
    void getAllReservations_ShouldReturnAllReservations() {
        Reservation reservation1 = reservation(1L, 1L, 10L, "Пара", ReservationStatus.CREATED);
        Reservation reservation2 = reservation(2L, 2L, 11L, "Екзамен", ReservationStatus.CANCELED);

        when(reservationRepository.findAll()).thenReturn(List.of(reservation1, reservation2));

        List<Reservation> result = reservationService.getAllReservations();

        assertEquals(2, result.size());
        assertEquals(List.of(reservation1, reservation2), result);
        verify(reservationRepository).findAll();
    }

    @Test
    void getReservationById_ShouldReturnReservation_WhenReservationExists() {
        Reservation reservation = reservation(1L, 1L, 10L, "Пара", ReservationStatus.CREATED);
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));

        Reservation result = reservationService.getReservationById(1L);

        assertEquals(reservation, result);
        verify(reservationRepository).findById(1L);
    }

    @Test
    void getReservationById_ShouldThrowResourceNotFoundException_WhenReservationDoesNotExist() {
        when(reservationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reservationService.getReservationById(99L));
        verify(reservationRepository).findById(99L);
    }

    @Test
    void getUserReservations_ShouldReturnReservationsOfUser() {
        List<Reservation> reservations = List.of(
                reservation(1L, 5L, 10L, "Пара", ReservationStatus.CREATED),
                reservation(2L, 5L, 11L, "Залік", ReservationStatus.CREATED)
        );
        when(reservationRepository.findByUserId(5L)).thenReturn(reservations);

        List<Reservation> result = reservationService.getUserReservations(5L);

        assertEquals(reservations, result);
        verify(reservationRepository).findByUserId(5L);
    }

    @Test
    void getMyReservations_ShouldNormalizeEmailAndReturnDtos() {
        User user = user(7L, "ivan@test.com");
        Reservation reservation = reservation(1L, 7L, 10L, "Пара", ReservationStatus.CREATED);
        reservation.setCreatedAt(LocalDateTime.of(2026, 5, 12, 10, 0));

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findByUserId(7L)).thenReturn(List.of(reservation));

        List<ReservationResponseDto> result = reservationService.getMyReservations("  IVAN@Test.com  ");

        assertEquals(1, result.size());
        ReservationResponseDto dto = result.get(0);
        assertEquals(1L, dto.id());
        assertEquals("Пара", dto.purpose());
        assertEquals("CREATED", dto.status());
        assertEquals(10L, dto.classroomId());
        assertEquals("101", dto.classroomName());
        verify(userRepository).findByEmail("ivan@test.com");
        verify(reservationRepository).findByUserId(7L);
    }

    @Test
    void getMyReservations_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.getMyReservations("missing@test.com"));

        verify(userRepository).findByEmail("missing@test.com");
        verify(reservationRepository, never()).findByUserId(any());
    }

    @Test
    void getClassroomReservations_ShouldReturnReservationsOfClassroom() {
        List<Reservation> reservations = List.of(
                reservation(1L, 1L, 10L, "Пара", ReservationStatus.CREATED),
                reservation(2L, 2L, 10L, "Нарада", ReservationStatus.CREATED)
        );
        when(reservationRepository.findByClassroomId(10L)).thenReturn(reservations);

        List<Reservation> result = reservationService.getClassroomReservations(10L);

        assertEquals(reservations, result);
        verify(reservationRepository).findByClassroomId(10L);
    }

    @Test
    void createReservation_ShouldCreateReservation_WhenRequestIsValid() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                "  Пара з математики  "
        );
        User user = user(5L, "ivan@test.com");
        Classroom classroom = classroom(10L, "101", true);

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(classroomRepository.findById(10L)).thenReturn(Optional.of(classroom));
        when(reservationRepository.existsConflict(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                ReservationStatus.CREATED
        )).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation saved = reservationService.createReservation(request, "  IVAN@test.com ");

        assertEquals(user, saved.getUser());
        assertEquals(classroom, saved.getClassroom());
        assertEquals(LocalDate.of(2026, 5, 20), saved.getReservationDate());
        assertEquals(LocalTime.of(10, 0), saved.getStartTime());
        assertEquals(LocalTime.of(11, 30), saved.getEndTime());
        assertEquals("Пара з математики", saved.getPurpose());
        assertEquals(ReservationStatus.CREATED, saved.getStatus());

        verify(userRepository).findByEmail("ivan@test.com");
        verify(classroomRepository).findById(10L);
        verify(reservationRepository).existsConflict(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 30),
                ReservationStatus.CREATED
        );
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenEmailCannotBeResolved() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(request, "   "));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenRequestIsNull() {
        assertThrows(BadRequestException.class, () -> reservationService.createReservation(null, "ivan@test.com"));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenRequiredFieldsAreMissing() {
        ReservationRequestDto request = new ReservationRequestDto(10L, null, null, null, "Пара");

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenClassroomIdIsMissing() {
        ReservationRequestDto request = new ReservationRequestDto(
                null,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenPurposeIsBlank() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "   "
        );

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenTimeIntervalIsInvalid() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(11, 0),
                LocalTime.of(10, 0),
                "Пара"
        );

        assertThrows(BadRequestException.class, () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository, never()).findByEmail(any());
        verify(classroomRepository, never()).findById(any());
    }

    @Test
    void createReservation_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );
        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository).findByEmail("ivan@test.com");
        verify(classroomRepository, never()).findById(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_ShouldThrowResourceNotFoundException_WhenClassroomDoesNotExist() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );
        User user = user(5L, "ivan@test.com");

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(classroomRepository.findById(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(userRepository).findByEmail("ivan@test.com");
        verify(classroomRepository).findById(10L);
        verify(reservationRepository, never()).existsConflict(any(), any(), any(), any(), any());
    }

    @Test
    void createReservation_ShouldThrowBadRequestException_WhenClassroomIsInactive() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );
        User user = user(5L, "ivan@test.com");
        Classroom classroom = classroom(10L, "101", false);

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(classroomRepository.findById(10L)).thenReturn(Optional.of(classroom));

        assertThrows(BadRequestException.class,
                () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(reservationRepository, never()).existsConflict(any(), any(), any(), any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void createReservation_ShouldThrowConflictException_WhenTimeSlotIsAlreadyBooked() {
        ReservationRequestDto request = new ReservationRequestDto(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                "Пара"
        );
        User user = user(5L, "ivan@test.com");
        Classroom classroom = classroom(10L, "101", true);

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(classroomRepository.findById(10L)).thenReturn(Optional.of(classroom));
        when(reservationRepository.existsConflict(
                10L,
                LocalDate.of(2026, 5, 20),
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                ReservationStatus.CREATED
        )).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> reservationService.createReservation(request, "ivan@test.com"));

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void deleteReservation_ShouldDeleteReservation_WhenReservationExists() {
        when(reservationRepository.existsById(15L)).thenReturn(true);

        reservationService.deleteReservation(15L);

        verify(reservationRepository).existsById(15L);
        verify(reservationRepository).deleteById(15L);
    }

    @Test
    void deleteReservation_ShouldThrowResourceNotFoundException_WhenReservationDoesNotExist() {
        when(reservationRepository.existsById(15L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> reservationService.deleteReservation(15L));

        verify(reservationRepository).existsById(15L);
        verify(reservationRepository, never()).deleteById(any());
    }

    @Test
    void deleteMyReservation_ShouldDeleteReservation_WhenReservationBelongsToCurrentUser() {
        User user = user(5L, "ivan@test.com");
        Reservation reservation = reservation(22L, 5L, 10L, "Пара", ReservationStatus.CREATED);

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById(22L)).thenReturn(Optional.of(reservation));

        reservationService.deleteMyReservation(22L, "  IVAN@test.com  ");

        verify(userRepository).findByEmail("ivan@test.com");
        verify(reservationRepository).findById(22L);
        verify(reservationRepository).deleteById(22L);
    }

    @Test
    void deleteMyReservation_ShouldThrowResourceNotFoundException_WhenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.deleteMyReservation(22L, "missing@test.com"));

        verify(userRepository).findByEmail("missing@test.com");
        verify(reservationRepository, never()).findById(any());
        verify(reservationRepository, never()).deleteById(any());
    }

    @Test
    void deleteMyReservation_ShouldThrowResourceNotFoundException_WhenReservationDoesNotExist() {
        User user = user(5L, "ivan@test.com");
        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById(22L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reservationService.deleteMyReservation(22L, "ivan@test.com"));

        verify(reservationRepository).findById(22L);
        verify(reservationRepository, never()).deleteById(any());
    }

    @Test
    void deleteMyReservation_ShouldThrowBadRequestException_WhenReservationBelongsToAnotherUser() {
        User currentUser = user(5L, "ivan@test.com");
        Reservation reservation = reservation(22L, 9L, 10L, "Пара", ReservationStatus.CREATED);

        when(userRepository.findByEmail("ivan@test.com")).thenReturn(Optional.of(currentUser));
        when(reservationRepository.findById(22L)).thenReturn(Optional.of(reservation));

        assertThrows(BadRequestException.class,
                () -> reservationService.deleteMyReservation(22L, "ivan@test.com"));

        verify(reservationRepository, never()).deleteById(any());
    }

    private User user(Long id, String email) {
        return User.builder()
                .id(id)
                .firstName("Іван")
                .lastName("Петренко")
                .email(email)
                .passwordHash("hash")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Classroom classroom(Long id, String name, boolean isActive) {
        return Classroom.builder()
                .id(id)
                .name(name)
                .capacity(30)
                .floor(2)
                .description("Аудиторія")
                .isActive(isActive)
                .build();
    }

    private Reservation reservation(Long id, Long userId, Long classroomId, String purpose, ReservationStatus status) {
        Reservation reservation = Reservation.builder()
                .id(id)
                .user(user(userId, "user" + userId + "@test.com"))
                .classroom(classroom(classroomId, "101", true))
                .reservationDate(LocalDate.of(2026, 5, 20))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .purpose(purpose)
                .status(status)
                .build();
        assertTrue(reservation.isTimeValid());
        return reservation;
    }
}
