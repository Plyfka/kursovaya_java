package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.BuildingRepository;
import ua.edu.duikt.booking.repository.ClassroomRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @InjectMocks
    private BuildingService buildingService;

    @Test
    void getAllBuildings_ShouldReturnAllBuildings() {
        List<Building> buildings = List.of(
                building(1L, "Корпус 1", "вул. Перша, 1"),
                building(2L, "Корпус 2", "вул. Друга, 2")
        );

        when(buildingRepository.findAll()).thenReturn(buildings);

        List<Building> result = buildingService.getAllBuildings();

        assertEquals(buildings, result);
        verify(buildingRepository).findAll();
    }

    @Test
    void getBuildingById_ShouldReturnBuilding_WhenExists() {
        Building building = building(1L, "Корпус 1", "вул. Перша, 1");
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));

        Building result = buildingService.getBuildingById(1L);

        assertEquals(building, result);
        verify(buildingRepository).findById(1L);
    }

    @Test
    void getBuildingById_ShouldThrowResourceNotFoundException_WhenMissing() {
        when(buildingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> buildingService.getBuildingById(99L));
        verify(buildingRepository).findById(99L);
    }

    @Test
    void createBuilding_ShouldNormalizeFieldsAndSave_WhenDataIsValid() {
        Building request = building(null, "  Корпус 1  ", "  вул. Перша, 1  ");

        when(buildingRepository.findByNameIgnoreCase("Корпус 1")).thenReturn(Optional.empty());
        when(buildingRepository.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Building saved = buildingService.createBuilding(request);

        assertEquals("Корпус 1", saved.getName());
        assertEquals("вул. Перша, 1", saved.getAddress());
        verify(buildingRepository).save(request);
    }

    @Test
    void createBuilding_ShouldSetNullAddress_WhenAddressBlank() {
        Building request = building(null, "Корпус 1", "   ");

        when(buildingRepository.findByNameIgnoreCase("Корпус 1")).thenReturn(Optional.empty());
        when(buildingRepository.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Building saved = buildingService.createBuilding(request);

        assertEquals("Корпус 1", saved.getName());
        assertNull(saved.getAddress());
        verify(buildingRepository).save(request);
    }

    @Test
    void createBuilding_ShouldThrowBadRequestException_WhenNameBlank() {
        Building request = building(null, "   ", "вул. Перша, 1");

        assertThrows(BadRequestException.class, () -> buildingService.createBuilding(request));

        verify(buildingRepository, never()).findByNameIgnoreCase(any());
        verify(buildingRepository, never()).save(any());
    }

    @Test
    void createBuilding_ShouldThrowConflictException_WhenNameAlreadyExists() {
        Building request = building(null, "Корпус 1", "вул. Перша, 1");
        when(buildingRepository.findByNameIgnoreCase("Корпус 1"))
                .thenReturn(Optional.of(building(10L, "Корпус 1", "інша адреса")));

        assertThrows(ConflictException.class, () -> buildingService.createBuilding(request));

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void updateBuilding_ShouldNormalizeFieldsAndSave_WhenDataIsValid() {
        Building existing = building(1L, "Старий корпус", "стара адреса");
        Building request = building(null, "  Новий корпус  ", "  нова адреса  ");

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findByNameIgnoreCase("Новий корпус")).thenReturn(Optional.empty());
        when(buildingRepository.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Building updated = buildingService.updateBuilding(1L, request);

        assertEquals("Новий корпус", updated.getName());
        assertEquals("нова адреса", updated.getAddress());
        verify(buildingRepository).save(existing);
    }

    @Test
    void updateBuilding_ShouldThrowConflictException_WhenNameBelongsToAnotherBuilding() {
        Building existing = building(1L, "Старий корпус", "стара адреса");
        Building request = building(null, "Корпус 2", "нова адреса");

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findByNameIgnoreCase("Корпус 2"))
                .thenReturn(Optional.of(building(2L, "Корпус 2", "інша адреса")));

        assertThrows(ConflictException.class, () -> buildingService.updateBuilding(1L, request));

        verify(buildingRepository, never()).save(any());
    }

    @Test
    void deleteBuilding_ShouldDelete_WhenNoLinkedClassrooms() {
        Building building = building(1L, "Корпус 1", "вул. Перша, 1");

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByBuildingId(1L)).thenReturn(List.of());

        buildingService.deleteBuilding(1L);

        verify(buildingRepository).delete(building);
    }

    @Test
    void deleteBuilding_ShouldThrowConflictException_WhenClassroomsExist() {
        Building building = building(1L, "Корпус 1", "вул. Перша, 1");

        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByBuildingId(1L)).thenReturn(List.of(mock(Classroom.class)));

        assertThrows(ConflictException.class, () -> buildingService.deleteBuilding(1L));

        verify(buildingRepository, never()).delete(any());
    }

    private Building building(Long id, String name, String address) {
        return Building.builder()
                .id(id)
                .name(name)
                .address(address)
                .build();
    }
}
