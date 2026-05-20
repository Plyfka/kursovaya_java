package ua.edu.duikt.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.exception.BadRequestException;
import ua.edu.duikt.booking.exception.ConflictException;
import ua.edu.duikt.booking.exception.ResourceNotFoundException;
import ua.edu.duikt.booking.repository.BuildingRepository;
import ua.edu.duikt.booking.repository.ClassroomRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;
    private final ClassroomRepository classroomRepository;

    public List<Building> getAllBuildings() {
        return buildingRepository.findAll();
    }

    public Building getBuildingById(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Корпус з id=" + id + " не знайдено"));
    }

    @Transactional
    public Building createBuilding(Building building) {
        String normalizedName = normalizeRequired(building.getName(), "Назва корпусу є обов'язковою");
        String normalizedAddress = normalizeNullable(building.getAddress());

        if (buildingRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ConflictException("Корпус з назвою '" + normalizedName + "' вже існує");
        }

        building.setName(normalizedName);
        building.setAddress(normalizedAddress);
        return buildingRepository.save(building);
    }

    @Transactional
    public Building updateBuilding(Long id, Building updatedBuilding) {
        Building building = getBuildingById(id);

        String normalizedName = normalizeRequired(updatedBuilding.getName(), "Назва корпусу є обов'язковою");
        String normalizedAddress = normalizeNullable(updatedBuilding.getAddress());

        buildingRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Корпус з назвою '" + normalizedName + "' вже існує");
                });

        building.setName(normalizedName);
        building.setAddress(normalizedAddress);
        return buildingRepository.save(building);
    }

    @Transactional
    public void deleteBuilding(Long id) {
        Building building = getBuildingById(id);

        if (!classroomRepository.findByBuildingId(id).isEmpty()) {
            throw new ConflictException("Неможливо видалити корпус, до нього прив'язані аудиторії. Спочатку перенесіть або видаліть аудиторії");
        }

        buildingRepository.delete(building);
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
