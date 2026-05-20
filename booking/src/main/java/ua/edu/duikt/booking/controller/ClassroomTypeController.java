package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.ClassroomTypeRequestDto;
import ua.edu.duikt.booking.entity.ClassroomType;
import ua.edu.duikt.booking.service.ClassroomTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/classroom-types")
@RequiredArgsConstructor
@Tag(name = "Classroom Types", description = "Операції для роботи з типами аудиторій")
public class ClassroomTypeController {

    private final ClassroomTypeService classroomTypeService;

    @GetMapping
    @Operation(summary = "Отримати список усіх типів аудиторій")
    public List<ClassroomType> getAllClassroomTypes() {
        return classroomTypeService.getAllClassroomTypes();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати тип аудиторії за id")
    public ClassroomType getClassroomTypeById(@PathVariable Long id) {
        return classroomTypeService.getClassroomTypeById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити новий тип аудиторії")
    public ClassroomType createClassroomType(@RequestBody ClassroomTypeRequestDto request) {
        return classroomTypeService.createClassroomType(toEntity(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Оновити тип аудиторії")
    public ClassroomType updateClassroomType(@PathVariable Long id, @RequestBody ClassroomTypeRequestDto request) {
        return classroomTypeService.updateClassroomType(id, toEntity(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалити тип аудиторії")
    public ResponseEntity<Void> deleteClassroomType(@PathVariable Long id) {
        classroomTypeService.deleteClassroomType(id);
        return ResponseEntity.noContent().build();
    }

    private ClassroomType toEntity(ClassroomTypeRequestDto request) {
        ClassroomType classroomType = new ClassroomType();
        if (request != null) {
            classroomType.setName(request.name());
            classroomType.setDescription(request.description());
        }
        return classroomType;
    }
}
