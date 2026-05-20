package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.duikt.booking.entity.Equipment;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomEquipmentRepository;
import ua.edu.duikt.booking.repository.EquipmentRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private ClassroomEquipmentRepository classroomEquipmentRepository;

    @InjectMocks
    private EquipmentService equipmentService;

    @Test
    void getAllEquipment_ShouldReturnAllEquipment() {
        List<Equipment> equipment = List.of(
                equipment(1L, "Проєктор", "Стельовий"),
                equipment(2L, "Маркерна дошка", "Біла")
        );

        when(equipmentRepository.findAll()).thenReturn(equipment);

        List<Equipment> result = equipmentService.getAllEquipment();

        assertEquals(equipment, result);
        verify(equipmentRepository).findAll();
    }

    @Test
    void getEquipmentById_ShouldReturnEquipment_WhenExists() {
        Equipment equipment = equipment(1L, "Проєктор", "Стельовий");
        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));

        Equipment result = equipmentService.getEquipmentById(1L);

        assertEquals(equipment, result);
        verify(equipmentRepository).findById(1L);
    }

    @Test
    void getEquipmentById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        when(equipmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> equipmentService.getEquipmentById(99L));
        verify(equipmentRepository).findById(99L);
    }

    @Test
    void createEquipment_ShouldNormalizeFieldsAndSave() {
        Equipment equipment = equipment(null, "  Проєктор  ", "  Стельовий  ");

        when(equipmentRepository.findByNameIgnoreCase("Проєктор")).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment saved = equipmentService.createEquipment(equipment);

        assertEquals("Проєктор", saved.getName());
        assertEquals("Стельовий", saved.getDescription());
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void createEquipment_ShouldSetDescriptionToNull_WhenBlank() {
        Equipment equipment = equipment(null, "Проєктор", "   ");

        when(equipmentRepository.findByNameIgnoreCase("Проєктор")).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment saved = equipmentService.createEquipment(equipment);

        assertEquals("Проєктор", saved.getName());
        assertNull(saved.getDescription());
    }

    @Test
    void createEquipment_ShouldThrowBadRequestException_WhenNameIsBlank() {
        Equipment equipment = equipment(null, "   ", "desc");

        assertThrows(BadRequestException.class, () -> equipmentService.createEquipment(equipment));

        verify(equipmentRepository, never()).findByNameIgnoreCase(any());
        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void createEquipment_ShouldThrowConflictException_WhenNameAlreadyExists() {
        Equipment equipment = equipment(null, "Проєктор", "desc");
        when(equipmentRepository.findByNameIgnoreCase("Проєктор")).thenReturn(Optional.of(equipment(5L, "Проєктор", "інший")));

        assertThrows(ConflictException.class, () -> equipmentService.createEquipment(equipment));

        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void updateEquipment_ShouldNormalizeFieldsAndSave() {
        Equipment existing = equipment(1L, "Старий", "Опис");
        Equipment updated = equipment(null, "  Новий проєктор ", "  Новий опис  ");

        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(equipmentRepository.findByNameIgnoreCase("Новий проєктор")).thenReturn(Optional.empty());
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Equipment result = equipmentService.updateEquipment(1L, updated);

        assertEquals("Новий проєктор", result.getName());
        assertEquals("Новий опис", result.getDescription());
        verify(equipmentRepository).save(existing);
    }

    @Test
    void updateEquipment_ShouldThrowConflictException_WhenAnotherEquipmentHasSameName() {
        Equipment existing = equipment(1L, "Старий", "Опис");
        Equipment updated = equipment(null, "Проєктор", "Опис");

        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(equipmentRepository.findByNameIgnoreCase("Проєктор"))
                .thenReturn(Optional.of(equipment(2L, "Проєктор", "інший")));

        assertThrows(ConflictException.class, () -> equipmentService.updateEquipment(1L, updated));

        verify(equipmentRepository, never()).save(any());
    }

    @Test
    void deleteEquipment_ShouldDelete_WhenEquipmentIsNotUsed() {
        Equipment equipment = equipment(1L, "Проєктор", "Опис");

        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(classroomEquipmentRepository.findByEquipmentId(1L)).thenReturn(List.of());

        equipmentService.deleteEquipment(1L);

        verify(equipmentRepository).delete(equipment);
    }

    @Test
    void deleteEquipment_ShouldThrowConflictException_WhenEquipmentIsUsed() {
        Equipment equipment = equipment(1L, "Проєктор", "Опис");

        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(equipment));
        when(classroomEquipmentRepository.findByEquipmentId(1L)).thenReturn(List.of(mockClassroomEquipment()));

        assertThrows(ConflictException.class, () -> equipmentService.deleteEquipment(1L));

        verify(equipmentRepository, never()).delete(any());
    }

    private Equipment equipment(Long id, String name, String description) {
        return Equipment.builder()
                .id(id)
                .name(name)
                .description(description)
                .build();
    }

    private ua.edu.duikt.booking.entity.ClassroomEquipment mockClassroomEquipment() {
        return ua.edu.duikt.booking.entity.ClassroomEquipment.builder().build();
    }
}
