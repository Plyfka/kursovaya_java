package ua.edu.duikt.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomRepository;
import ua.edu.duikt.booking.repository.ClassroomTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;
    private final ClassroomRepository classroomRepository;

    public List<ClassroomType> getAllClassroomTypes() {
        return classroomTypeRepository.findAll();
    }

    public ClassroomType getClassroomTypeById(Long id) {
        return classroomTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Тип аудиторії з id=" + id + " не знайдено"));
    }

    @Transactional
    public ClassroomType createClassroomType(ClassroomType classroomType) {
        String normalizedName = normalizeRequired(classroomType.getName(), "Назва типу аудиторії є обов'язковою");
        String normalizedDescription = normalizeNullable(classroomType.getDescription());

        if (classroomTypeRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ConflictException("Тип аудиторії з назвою '" + normalizedName + "' вже існує");
        }

        classroomType.setName(normalizedName);
        classroomType.setDescription(normalizedDescription);
        return classroomTypeRepository.save(classroomType);
    }

    @Transactional
    public ClassroomType updateClassroomType(Long id, ClassroomType updatedClassroomType) {
        ClassroomType classroomType = getClassroomTypeById(id);

        String normalizedName = normalizeRequired(updatedClassroomType.getName(), "Назва типу аудиторії є обов'язковою");
        String normalizedDescription = normalizeNullable(updatedClassroomType.getDescription());

        classroomTypeRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Тип аудиторії з назвою '" + normalizedName + "' вже існує");
                });

        classroomType.setName(normalizedName);
        classroomType.setDescription(normalizedDescription);
        return classroomTypeRepository.save(classroomType);
    }

    @Transactional
    public void deleteClassroomType(Long id) {
        ClassroomType classroomType = getClassroomTypeById(id);

        if (!classroomRepository.findByClassroomTypeId(id).isEmpty()) {
            throw new ConflictException("Неможливо видалити тип аудиторії, який уже використовується в аудиторіях");
        }

        classroomTypeRepository.delete(classroomType);
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }
        return value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
