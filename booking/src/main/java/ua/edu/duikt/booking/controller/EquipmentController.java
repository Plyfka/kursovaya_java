package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.EquipmentRequestDto;
import ua.edu.duikt.booking.entity.Equipment;
import ua.edu.duikt.booking.service.EquipmentService;

import java.util.List;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@Tag(name = "Equipment", description = "Операції для роботи з обладнанням")
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping
    @Operation(summary = "Отримати список усього обладнання")
    public List<Equipment> getAllEquipment() {
        return equipmentService.getAllEquipment();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати обладнання за id")
    public Equipment getEquipmentById(@PathVariable Long id) {
        return equipmentService.getEquipmentById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити новий запис про обладнання")
    public Equipment createEquipment(@RequestBody EquipmentRequestDto request) {
        return equipmentService.createEquipment(toEntity(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Оновити дані обладнання")
    public Equipment updateEquipment(@PathVariable Long id, @RequestBody EquipmentRequestDto request) {
        return equipmentService.updateEquipment(id, toEntity(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалити обладнання")
    public ResponseEntity<Void> deleteEquipment(@PathVariable Long id) {
        equipmentService.deleteEquipment(id);
        return ResponseEntity.noContent().build();
    }

    private Equipment toEntity(EquipmentRequestDto request) {
        Equipment equipment = new Equipment();
        if (request != null) {
            equipment.setName(request.name());
            equipment.setDescription(request.description());
        }
        return equipment;
    }
}
