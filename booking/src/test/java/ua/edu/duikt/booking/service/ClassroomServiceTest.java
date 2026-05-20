package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.duikt.booking.dto.ClassroomDetailsDto;
import ua.edu.duikt.booking.dto.ClassroomRequestDto;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.ClassroomEquipment;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.entity.Equipment;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private ClassroomTypeRepository classroomTypeRepository;

    @InjectMocks
    private ClassroomService classroomService;

    @Test
    void getAllClassrooms_ShouldReturnAllClassrooms() {
        Classroom classroom1 = classroom(1L, "101", true, 30, 1);
        Classroom classroom2 = classroom(2L, "102", false, 20, 2);

        when(classroomRepository.findAll()).thenReturn(List.of(classroom1, classroom2));

        List<Classroom> result = classroomService.getAllClassrooms();

        assertEquals(2, result.size());
        assertEquals("101", result.get(0).getName());
        assertEquals("102", result.get(1).getName());
    }

    @Test
    void getActiveClassrooms_ShouldReturnOnlyActiveClassrooms() {
        Classroom classroom1 = classroom(1L, "101", true, 30, 1);
        Classroom classroom2 = classroom(2L, "102", true, 25, 2);

        when(classroomRepository.findByIsActiveTrue()).thenReturn(List.of(classroom1, classroom2));

        List<Classroom> result = classroomService.getActiveClassrooms();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(c -> Boolean.TRUE.equals(c.getIsActive())));
    }

    @Test
    void getClassroomById_ShouldReturnClassroom_WhenExists() {
        Classroom classroom = classroom(1L, "101", true, 30, 1);
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(classroom));

        Classroom result = classroomService.getClassroomById(1L);

        assertEquals(classroom, result);
    }

    @Test
    void getClassroomById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        when(classroomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomService.getClassroomById(99L));
    }

    @Test
    void getClassroomDetails_ShouldMapEntityToDto() {
        Classroom classroom = classroom(1L, "101", true, 30, 1);
        classroom.setDescription("Комп'ютерний клас");
        classroom.setClassroomEquipmentList(List.of(classroomEquipment(10L, "Проєктор", 2)));

        when(classroomRepository.findById(1L)).thenReturn(Optional.of(classroom));

        ClassroomDetailsDto result = classroomService.getClassroomDetails(1L);

        assertEquals(1L, result.id());
        assertEquals("101", result.name());
        assertEquals(30, result.capacity());
        assertEquals(1, result.floor());
        assertEquals("Комп'ютерний клас", result.description());
        assertEquals(1L, result.buildingId());
        assertEquals("Корпус 1", result.buildingName());
        assertEquals(1L, result.classroomTypeId());
        assertEquals("Лекційна", result.classroomTypeName());
        assertEquals(1, result.equipment().size());
        assertEquals("Проєктор", result.equipment().get(0).name());
        assertEquals(2, result.equipment().get(0).quantity());
    }

    @Test
    void getAvailableClassrooms_ShouldReturnOnlyNonConflictingClassrooms() {
        LocalDate date = LocalDate.of(2026, 5, 20);
        LocalTime start = LocalTime.of(10, 0);
        LocalTime end = LocalTime.of(11, 30);

        Classroom available = classroom(1L, "101", true, 30, 1);
        Classroom busy = classroom(2L, "102", true, 40, 2);

        when(classroomRepository.findByIsActiveTrue()).thenReturn(List.of(available, busy));
        when(reservationRepository.existsConflict(eq(1L), eq(date), eq(start), eq(end), eq(ReservationStatus.CREATED)))
                .thenReturn(false);
        when(reservationRepository.existsConflict(eq(2L), eq(date), eq(start), eq(end), eq(ReservationStatus.CREATED)))
                .thenReturn(true);
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(available));

        List<ClassroomDetailsDto> result = classroomService.getAvailableClassrooms(date, start, end);

        assertEquals(1, result.size());
        assertEquals("101", result.get(0).name());
    }

    @Test
    void getAvailableClassrooms_ShouldThrowBadRequestException_WhenTimeWindowInvalid() {
        assertThrows(
                BadRequestException.class,
                () -> classroomService.getAvailableClassrooms(
                        LocalDate.of(2026, 5, 20),
                        LocalTime.of(12, 0),
                        LocalTime.of(12, 0)
                )
        );

        verify(classroomRepository, never()).findByIsActiveTrue();
    }

    @Test
    void searchClassrooms_ShouldApplyFiltersAndReturnMatchingClassroom() {
        LocalDate date = LocalDate.of(2026, 5, 20);
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(10, 30);

        Classroom match = classroom(1L, "101", true, 35, 1);
        match.setClassroomEquipmentList(List.of(
                classroomEquipment(10L, "Проєктор", 1),
                classroomEquipment(11L, "Дошка", 1)
        ));

        Classroom wrongType = classroom(2L, "102", true, 35, 1);
        ClassroomType labType = new ClassroomType();
        labType.setId(2L);
        labType.setName("Лабораторна");
        wrongType.setClassroomType(labType);
        wrongType.setClassroomEquipmentList(List.of(classroomEquipment(10L, "Проєктор", 1)));

        when(classroomRepository.findByIsActiveTrue()).thenReturn(List.of(match, wrongType));
        when(reservationRepository.existsConflict(eq(1L), eq(date), eq(start), eq(end), eq(ReservationStatus.CREATED)))
                .thenReturn(false);
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(match));

        List<ClassroomDetailsDto> result = classroomService.searchClassrooms(
                date,
                start,
                end,
                30,
                " лекційна ",
                List.of(" проєктор ", "дошка")
        );

        assertEquals(1, result.size());
        assertEquals("101", result.get(0).name());
    }

    @Test
    void createClassroom_ShouldCreateAndSaveMappedEntity() {
        ClassroomRequestDto request = new ClassroomRequestDto(
                " 101 ",
                1L,
                2L,
                30,
                1,
                " Комп'ютерний клас ",
                null
        );

        Building building = building(1L, "Корпус 1");
        ClassroomType classroomType = classroomType(2L, "Лабораторна");

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeRepository.findById(2L)).thenReturn(Optional.of(classroomType));
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> {
            Classroom classroom = invocation.getArgument(0);
            classroom.setId(10L);
            return classroom;
        });

        Classroom saved = classroomService.createClassroom(request);

        assertEquals(10L, saved.getId());
        assertEquals("101", saved.getName());
        assertEquals(30, saved.getCapacity());
        assertEquals(1, saved.getFloor());
        assertEquals("Комп'ютерний клас", saved.getDescription());
        assertEquals(building, saved.getBuilding());
        assertEquals(classroomType, saved.getClassroomType());
        assertEquals(Boolean.TRUE, saved.getIsActive());

        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        assertEquals("101", captor.getValue().getName());
        assertEquals(Boolean.TRUE, captor.getValue().getIsActive());
    }

    @Test
    void createClassroom_ShouldThrowBadRequestException_WhenNameBlank() {
        ClassroomRequestDto request = new ClassroomRequestDto(
                "   ",
                1L,
                2L,
                30,
                1,
                null,
                true
        );

        assertThrows(BadRequestException.class, () -> classroomService.createClassroom(request));

        verify(classroomRepository, never()).save(any());
    }

    @Test
    void updateClassroom_ShouldUpdateExistingClassroom() {
        Classroom classroom = classroom(1L, "101", true, 30, 1);
        Building newBuilding = building(2L, "Корпус 2");
        ClassroomType newType = classroomType(3L, "Лабораторна");

        ClassroomRequestDto request = new ClassroomRequestDto(
                " 202 ",
                2L,
                3L,
                45,
                4,
                " Оновлена аудиторія ",
                false
        );

        when(classroomRepository.findById(1L)).thenReturn(Optional.of(classroom));
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(newBuilding));
        when(classroomTypeRepository.findById(3L)).thenReturn(Optional.of(newType));
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Classroom updated = classroomService.updateClassroom(1L, request);

        assertEquals("202", updated.getName());
        assertEquals(45, updated.getCapacity());
        assertEquals(4, updated.getFloor());
        assertEquals("Оновлена аудиторія", updated.getDescription());
        assertEquals(Boolean.FALSE, updated.getIsActive());
        assertEquals(newBuilding, updated.getBuilding());
        assertEquals(newType, updated.getClassroomType());
    }

    @Test
    void deleteClassroom_ShouldDelete_WhenNoReservationsExist() {
        Classroom classroom = classroom(1L, "101", true, 30, 1);
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(classroom));
        when(reservationRepository.findByClassroomId(1L)).thenReturn(List.of());

        classroomService.deleteClassroom(1L);

        verify(classroomRepository).delete(classroom);
    }

    @Test
    void deleteClassroom_ShouldThrowConflictException_WhenReservationsExist() {
        Classroom classroom = classroom(1L, "101", true, 30, 1);
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(classroom));
        when(reservationRepository.findByClassroomId(1L)).thenReturn(List.of(mockReservation()));

        assertThrows(ConflictException.class, () -> classroomService.deleteClassroom(1L));

        verify(classroomRepository, never()).delete(any());
    }

    private Classroom classroom(Long id, String name, Boolean isActive, Integer capacity, Integer floor) {
        Classroom classroom = new Classroom();
        classroom.setId(id);
        classroom.setName(name);
        classroom.setIsActive(isActive);
        classroom.setCapacity(capacity);
        classroom.setFloor(floor);
        classroom.setDescription(null);
        classroom.setBuilding(building(1L, "Корпус 1"));
        classroom.setClassroomType(classroomType(1L, "Лекційна"));
        classroom.setClassroomEquipmentList(List.of());
        return classroom;
    }

    private Building building(Long id, String name) {
        Building building = new Building();
        building.setId(id);
        building.setName(name);
        building.setAddress("Адреса");
        return building;
    }

    private ClassroomType classroomType(Long id, String name) {
        ClassroomType classroomType = new ClassroomType();
        classroomType.setId(id);
        classroomType.setName(name);
        classroomType.setDescription("Опис");
        return classroomType;
    }

    private ClassroomEquipment classroomEquipment(Long equipmentId, String equipmentName, Integer quantity) {
        Equipment equipment = new Equipment();
        equipment.setId(equipmentId);
        equipment.setName(equipmentName);
        equipment.setDescription("Опис");

        ClassroomEquipment classroomEquipment = new ClassroomEquipment();
        classroomEquipment.setEquipment(equipment);
        classroomEquipment.setQuantity(quantity);
        return classroomEquipment;
    }

    private ua.edu.duikt.booking.entity.Reservation mockReservation() {
        ua.edu.duikt.booking.entity.Reservation reservation = new ua.edu.duikt.booking.entity.Reservation();
        reservation.setId(1L);
        return reservation;
    }
}
