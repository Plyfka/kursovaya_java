package ua.edu.duikt.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.dto.ClassroomDetailsDto;
import ua.edu.duikt.booking.dto.ClassroomRequestDto;
import ua.edu.duikt.booking.dto.EquipmentItemDto;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.ClassroomEquipment;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.entity.ReservationStatus;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.BuildingRepository;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ClassroomTypeRepository;
import ua.edu.duikt.booking.repository.ReservationRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final ReservationRepository reservationRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomTypeRepository classroomTypeRepository;

    public List<Classroom> getAllClassrooms() {
        return classroomRepository.findAll();
    }

    public List<Classroom> getActiveClassrooms() {
        return classroomRepository.findByIsActiveTrue();
    }

    public Classroom getClassroomById(Long id) {
        return classroomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Аудиторію з id=" + id + " не знайдено"));
    }

    public ClassroomDetailsDto getClassroomDetails(Long id) {
        Classroom classroom = getClassroomById(id);

        List<EquipmentItemDto> equipment = classroom.getClassroomEquipmentList().stream()
                .map(this::toEquipmentDto)
                .toList();

        return new ClassroomDetailsDto(
                classroom.getId(),
                classroom.getName(),
                classroom.getCapacity(),
                classroom.getFloor(),
                classroom.getDescription(),
                classroom.getIsActive(),
                classroom.getBuilding().getId(),
                classroom.getBuilding().getName(),
                classroom.getClassroomType().getId(),
                classroom.getClassroomType().getName(),
                equipment
        );
    }

    public List<ClassroomDetailsDto> getAvailableClassrooms(LocalDate date, LocalTime startTime, LocalTime endTime) {
        validateReservationWindow(date, startTime, endTime);

        return classroomRepository.findByIsActiveTrue().stream()
                .filter(classroom -> !reservationRepository.existsConflict(
                        classroom.getId(),
                        date,
                        startTime,
                        endTime,
                        ReservationStatus.CREATED
                ))
                .map(classroom -> getClassroomDetails(classroom.getId()))
                .toList();
    }

    public List<ClassroomDetailsDto> searchClassrooms(LocalDate date,
                                                      LocalTime startTime,
                                                      LocalTime endTime,
                                                      Integer minCapacity,
                                                      String classroomTypeName,
                                                      List<String> equipmentNames) {
        validateReservationWindow(date, startTime, endTime);

        if (minCapacity != null && minCapacity <= 0) {
            throw new BadRequestException("Мінімальна місткість має бути більшою за 0");
        }

        List<String> normalizedEquipmentNames = equipmentNames == null
                ? List.of()
                : equipmentNames.stream()
                  .map(this::normalizeForComparison)
                  .filter(value -> value != null && !value.isBlank())
                  .toList();

        String normalizedClassroomTypeName = normalizeForComparison(classroomTypeName);

        return classroomRepository.findByIsActiveTrue().stream()
                .filter(classroom -> minCapacity == null || classroom.getCapacity() >= minCapacity)
                .filter(classroom -> normalizedClassroomTypeName == null || normalizedClassroomTypeName.isBlank()
                        || classroom.getClassroomType().getName().equalsIgnoreCase(normalizedClassroomTypeName))
                .filter(classroom -> normalizedEquipmentNames.isEmpty() || hasAllEquipment(classroom, normalizedEquipmentNames))
                .filter(classroom -> !reservationRepository.existsConflict(
                        classroom.getId(),
                        date,
                        startTime,
                        endTime,
                        ReservationStatus.CREATED
                ))
                .map(classroom -> getClassroomDetails(classroom.getId()))
                .toList();
    }

    private boolean hasAllEquipment(Classroom classroom, List<String> equipmentNames) {
        List<String> classroomEquipmentNames = classroom.getClassroomEquipmentList().stream()
                .map(ce -> normalizeForComparison(ce.getEquipment().getName()))
                .toList();

        return equipmentNames.stream().allMatch(classroomEquipmentNames::contains);
    }

    private EquipmentItemDto toEquipmentDto(ClassroomEquipment classroomEquipment) {
        return new EquipmentItemDto(
                classroomEquipment.getEquipment().getId(),
                classroomEquipment.getEquipment().getName(),
                classroomEquipment.getQuantity()
        );
    }

    @Transactional
    public Classroom createClassroom(ClassroomRequestDto classroomRequestDto) {
        String name = requireName(classroomRequestDto.name());
        Integer capacity = requirePositive(classroomRequestDto.capacity(), "Місткість аудиторії має бути більшою за 0");
        Integer floor = requireNonNegative(classroomRequestDto.floor(), "Поверх не може бути від'ємним");
        Building building = getBuilding(classroomRequestDto.buildingId());
        ClassroomType classroomType = getClassroomType(classroomRequestDto.classroomTypeId());

        Classroom classroom = Classroom.builder()
                .name(name)
                .building(building)
                .classroomType(classroomType)
                .capacity(capacity)
                .floor(floor)
                .description(normalizeDescription(classroomRequestDto.description()))
                .isActive(classroomRequestDto.isActive() == null ? Boolean.TRUE : classroomRequestDto.isActive())
                .build();

        return classroomRepository.save(classroom);
    }

    @Transactional
    public Classroom updateClassroom(Long id, ClassroomRequestDto classroomRequestDto) {
        Classroom classroom = getClassroomById(id);

        classroom.setName(requireName(classroomRequestDto.name()));
        classroom.setBuilding(getBuilding(classroomRequestDto.buildingId()));
        classroom.setClassroomType(getClassroomType(classroomRequestDto.classroomTypeId()));
        classroom.setCapacity(requirePositive(classroomRequestDto.capacity(), "Місткість аудиторії має бути більшою за 0"));
        classroom.setFloor(requireNonNegative(classroomRequestDto.floor(), "Поверх не може бути від'ємним"));
        classroom.setDescription(normalizeDescription(classroomRequestDto.description()));
        classroom.setIsActive(classroomRequestDto.isActive() == null ? classroom.getIsActive() : classroomRequestDto.isActive());

        return classroomRepository.save(classroom);
    }

    @Transactional
    public void deleteClassroom(Long id) {
        Classroom classroom = getClassroomById(id);

        if (!reservationRepository.findByClassroomId(id).isEmpty()) {
            throw new ConflictException("Неможливо видалити аудиторію, для якої вже існують бронювання. Деактивуйте її замість видалення");
        }

        classroomRepository.delete(classroom);
    }

    private Building getBuilding(Long buildingId) {
        if (buildingId == null) {
            throw new BadRequestException("Не вказано buildingId");
        }

        return buildingRepository.findById(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException("Корпус з id=" + buildingId + " не знайдено"));
    }

    private ClassroomType getClassroomType(Long classroomTypeId) {
        if (classroomTypeId == null) {
            throw new BadRequestException("Не вказано classroomTypeId");
        }

        return classroomTypeRepository.findById(classroomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Тип аудиторії з id=" + classroomTypeId + " не знайдено"));
    }

    private String requireName(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Назва аудиторії є обов'язковою");
        }
        return normalized;
    }

    private Integer requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private Integer requireNonNegative(Integer value, String message) {
        if (value == null || value < 0) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private String normalizeDescription(String description) {
        String normalized = normalizeNullable(description);
        return (normalized == null || normalized.isBlank()) ? null : normalized;
    }

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeForComparison(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private void validateReservationWindow(LocalDate date, LocalTime startTime, LocalTime endTime) {
        if (date == null) {
            throw new BadRequestException("Дата бронювання є обов'язковою");
        }
        if (startTime == null || endTime == null) {
            throw new BadRequestException("Час початку і завершення бронювання є обов'язковими");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("Некоректний часовий інтервал бронювання");
        }
    }
}
