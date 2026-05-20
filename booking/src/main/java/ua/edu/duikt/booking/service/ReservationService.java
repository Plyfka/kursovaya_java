package ua.edu.duikt.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.dto.ReservationRequestDto;
import ua.edu.duikt.booking.dto.ReservationResponseDto;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ReservationRepository;
import ua.edu.duikt.booking.repository.UserRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ClassroomRepository classroomRepository;
    private final UserRepository userRepository;

    public List<Reservation> getAllReservations() {
        return reservationRepository.findAll();
    }

    public Reservation getReservationById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Бронювання з id=" + id + " не знайдено"));
    }

    public List<Reservation> getUserReservations(Long userId) {
        return reservationRepository.findByUserId(userId);
    }

    public List<ReservationResponseDto> getMyReservations(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з email=" + email + " не знайдено"));

        return reservationRepository.findByUserId(user.getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public List<Reservation> getClassroomReservations(Long classroomId) {
        return reservationRepository.findByClassroomId(classroomId);
    }

    @Transactional
    public Reservation createReservation(ReservationRequestDto request, String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new BadRequestException("Не вдалося визначити поточного користувача");
        }

        if (request == null) {
            throw new BadRequestException("Тіло запиту не може бути порожнім");
        }

        if (request.reservationDate() == null
                || request.startTime() == null
                || request.endTime() == null) {
            throw new BadRequestException("Дата та час бронювання є обов'язковими");
        }

        if (request.classroomId() == null) {
            throw new BadRequestException("Потрібно вказати аудиторію для бронювання");
        }

        if (request.purpose() == null || request.purpose().isBlank()) {
            throw new BadRequestException("Потрібно вказати мету бронювання");
        }

        if (!request.endTime().isAfter(request.startTime())) {
            throw new BadRequestException("Некоректний часовий інтервал бронювання");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з email=" + normalizedEmail + " не знайдено"));

        Classroom classroom = classroomRepository.findById(request.classroomId())
                .orElseThrow(() -> new ResourceNotFoundException("Аудиторію з id=" + request.classroomId() + " не знайдено"));

        if (!Boolean.TRUE.equals(classroom.getIsActive())) {
            throw new BadRequestException("Неможливо створити бронювання для неактивної аудиторії");
        }

        boolean conflict = reservationRepository.existsConflict(
                classroom.getId(),
                request.reservationDate(),
                request.startTime(),
                request.endTime(),
                ReservationStatus.CREATED
        );

        if (conflict) {
            throw new ConflictException("Для цієї аудиторії вже існує бронювання на вказаний час");
        }

        Reservation reservation = Reservation.builder()
                .user(user)
                .classroom(classroom)
                .reservationDate(request.reservationDate())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .purpose(request.purpose().trim())
                .status(ReservationStatus.CREATED)
                .build();

        return reservationRepository.save(reservation);
    }

    @Transactional
    public void deleteReservation(Long id) {
        if (!reservationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Бронювання з id=" + id + " не знайдено");
        }
        reservationRepository.deleteById(id);
    }

    @Transactional
    public void deleteMyReservation(Long reservationId, String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Користувача з email=" + normalizedEmail + " не знайдено"));

        Reservation reservation = getReservationById(reservationId);

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Неможливо видалити чуже бронювання");
        }

        reservationRepository.deleteById(reservationId);
    }

    private ReservationResponseDto toDto(Reservation reservation) {
        return new ReservationResponseDto(
                reservation.getId(),
                reservation.getReservationDate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getPurpose(),
                reservation.getStatus().name(),
                reservation.getCreatedAt(),
                reservation.getClassroom().getId(),
                reservation.getClassroom().getName()
        );
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
