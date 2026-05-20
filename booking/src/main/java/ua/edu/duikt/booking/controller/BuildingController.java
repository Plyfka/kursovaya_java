package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.BuildingRequestDto;
import ua.edu.duikt.booking.entity.Building;
import ua.edu.duikt.booking.service.BuildingService;

import java.util.List;

@RestController
@RequestMapping("/api/buildings")
@RequiredArgsConstructor
@Tag(name = "Buildings", description = "Операції для роботи з корпусами")
public class BuildingController {

    private final BuildingService buildingService;

    @GetMapping
    @Operation(summary = "Отримати список усіх корпусів")
    public List<Building> getAllBuildings() {
        return buildingService.getAllBuildings();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати корпус за id")
    public Building getBuildingById(@PathVariable Long id) {
        return buildingService.getBuildingById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити новий корпус")
    public Building createBuilding(@RequestBody BuildingRequestDto request) {
        return buildingService.createBuilding(toEntity(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Оновити дані корпусу")
    public Building updateBuilding(@PathVariable Long id, @RequestBody BuildingRequestDto request) {
        return buildingService.updateBuilding(id, toEntity(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалити корпус")
    public ResponseEntity<Void> deleteBuilding(@PathVariable Long id) {
        buildingService.deleteBuilding(id);
        return ResponseEntity.noContent().build();
    }

    private Building toEntity(BuildingRequestDto request) {
        Building building = new Building();
        if (request != null) {
            building.setName(request.name());
            building.setAddress(request.address());
        }
        return building;
    }
}
