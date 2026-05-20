package ua.edu.duikt.booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ClassroomTypeRepository;

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
class ClassroomTypeServiceTest {

    @Mock
    private ClassroomTypeRepository classroomTypeRepository;

    @Mock
    private ClassroomRepository classroomRepository;

    @InjectMocks
    private ClassroomTypeService classroomTypeService;

    @Test
    void getAllClassroomTypes_ShouldReturnAllTypes() {
        List<ClassroomType> types = List.of(
                classroomType(1L, "Лекційна", "Велика аудиторія"),
                classroomType(2L, "Лабораторія", "Для практичних занять")
        );

        when(classroomTypeRepository.findAll()).thenReturn(types);

        List<ClassroomType> result = classroomTypeService.getAllClassroomTypes();

        assertEquals(types, result);
        verify(classroomTypeRepository).findAll();
    }

    @Test
    void getClassroomTypeById_ShouldReturnType_WhenExists() {
        ClassroomType type = classroomType(1L, "Лекційна", "Велика аудиторія");
        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        ClassroomType result = classroomTypeService.getClassroomTypeById(1L);

        assertEquals(type, result);
        verify(classroomTypeRepository).findById(1L);
    }

    @Test
    void getClassroomTypeById_ShouldThrowResourceNotFoundException_WhenNotExists() {
        when(classroomTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> classroomTypeService.getClassroomTypeById(99L));
        verify(classroomTypeRepository).findById(99L);
    }

    @Test
    void createClassroomType_ShouldNormalizeFieldsAndSave() {
        ClassroomType type = classroomType(null, "  Лекційна  ", "  Велика аудиторія  ");

        when(classroomTypeRepository.findByNameIgnoreCase("Лекційна")).thenReturn(Optional.empty());
        when(classroomTypeRepository.save(any(ClassroomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClassroomType saved = classroomTypeService.createClassroomType(type);

        assertEquals("Лекційна", saved.getName());
        assertEquals("Велика аудиторія", saved.getDescription());
        verify(classroomTypeRepository).save(type);
    }

    @Test
    void createClassroomType_ShouldSetDescriptionToNull_WhenBlank() {
        ClassroomType type = classroomType(null, "Лекційна", "   ");

        when(classroomTypeRepository.findByNameIgnoreCase("Лекційна")).thenReturn(Optional.empty());
        when(classroomTypeRepository.save(any(ClassroomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClassroomType saved = classroomTypeService.createClassroomType(type);

        assertEquals("Лекційна", saved.getName());
        assertNull(saved.getDescription());
    }

    @Test
    void createClassroomType_ShouldThrowBadRequestException_WhenNameIsBlank() {
        ClassroomType type = classroomType(null, "   ", "desc");

        assertThrows(BadRequestException.class, () -> classroomTypeService.createClassroomType(type));

        verify(classroomTypeRepository, never()).findByNameIgnoreCase(any());
        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    void createClassroomType_ShouldThrowConflictException_WhenNameAlreadyExists() {
        ClassroomType type = classroomType(null, "Лекційна", "desc");
        when(classroomTypeRepository.findByNameIgnoreCase("Лекційна"))
                .thenReturn(Optional.of(classroomType(5L, "Лекційна", "інший")));

        assertThrows(ConflictException.class, () -> classroomTypeService.createClassroomType(type));

        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    void updateClassroomType_ShouldNormalizeFieldsAndSave() {
        ClassroomType existing = classroomType(1L, "Старий", "Опис");
        ClassroomType updated = classroomType(null, "  Новий тип  ", "  Новий опис  ");

        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(classroomTypeRepository.findByNameIgnoreCase("Новий тип")).thenReturn(Optional.empty());
        when(classroomTypeRepository.save(any(ClassroomType.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClassroomType result = classroomTypeService.updateClassroomType(1L, updated);

        assertEquals("Новий тип", result.getName());
        assertEquals("Новий опис", result.getDescription());
        verify(classroomTypeRepository).save(existing);
    }

    @Test
    void updateClassroomType_ShouldThrowConflictException_WhenAnotherTypeHasSameName() {
        ClassroomType existing = classroomType(1L, "Старий", "Опис");
        ClassroomType updated = classroomType(null, "Лекційна", "Опис");

        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(classroomTypeRepository.findByNameIgnoreCase("Лекційна"))
                .thenReturn(Optional.of(classroomType(2L, "Лекційна", "інший")));

        assertThrows(ConflictException.class, () -> classroomTypeService.updateClassroomType(1L, updated));

        verify(classroomTypeRepository, never()).save(any());
    }

    @Test
    void deleteClassroomType_ShouldDelete_WhenTypeIsNotUsed() {
        ClassroomType type = classroomType(1L, "Лекційна", "Опис");

        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(classroomRepository.findByClassroomTypeId(1L)).thenReturn(List.of());

        classroomTypeService.deleteClassroomType(1L);

        verify(classroomTypeRepository).delete(type);
    }

    @Test
    void deleteClassroomType_ShouldThrowConflictException_WhenTypeIsUsed() {
        ClassroomType type = classroomType(1L, "Лекційна", "Опис");

        when(classroomTypeRepository.findById(1L)).thenReturn(Optional.of(type));
        when(classroomRepository.findByClassroomTypeId(1L)).thenReturn(List.of(classroom(10L, "101")));

        assertThrows(ConflictException.class, () -> classroomTypeService.deleteClassroomType(1L));

        verify(classroomTypeRepository, never()).delete(any());
    }

    private ClassroomType classroomType(Long id, String name, String description) {
        return ClassroomType.builder()
                .id(id)
                .name(name)
                .description(description)
                .build();
    }

    private Classroom classroom(Long id, String name) {
        return Classroom.builder()
                .id(id)
                .name(name)
                .build();
    }
}
