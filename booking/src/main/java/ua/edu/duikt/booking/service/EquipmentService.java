package ua.edu.duikt.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.entity.Equipment;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.ClassroomEquipmentRepository;
import ua.edu.duikt.booking.repository.EquipmentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final ClassroomEquipmentRepository classroomEquipmentRepository;

    public List<Equipment> getAllEquipment() {
        return equipmentRepository.findAll();
    }

    public Equipment getEquipmentById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Обладнання з id=" + id + " не знайдено"));
    }

    @Transactional
    public Equipment createEquipment(Equipment equipment) {
        String normalizedName = normalizeRequired(equipment.getName(), "Назва обладнання є обов'язковою");
        String normalizedDescription = normalizeNullable(equipment.getDescription());

        if (equipmentRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ConflictException("Обладнання з назвою '" + normalizedName + "' вже існує");
        }

        equipment.setName(normalizedName);
        equipment.setDescription(normalizedDescription);
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public Equipment updateEquipment(Long id, Equipment updatedEquipment) {
        Equipment equipment = getEquipmentById(id);

        String normalizedName = normalizeRequired(updatedEquipment.getName(), "Назва обладнання є обов'язковою");
        String normalizedDescription = normalizeNullable(updatedEquipment.getDescription());

        equipmentRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Обладнання з назвою '" + normalizedName + "' вже існує");
                });

        equipment.setName(normalizedName);
        equipment.setDescription(normalizedDescription);
        return equipmentRepository.save(equipment);
    }

    @Transactional
    public void deleteEquipment(Long id) {
        Equipment equipment = getEquipmentById(id);

        if (!classroomEquipmentRepository.findByEquipmentId(id).isEmpty()) {
            throw new ConflictException("Неможливо видалити обладнання, яке вже прив'язане до аудиторій");
        }

        equipmentRepository.delete(equipment);
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
