package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.ClassroomDetailsDto;
import ua.edu.duikt.booking.dto.ClassroomRequestDto;
import ua.edu.duikt.booking.entity.Classroom;
import ua.edu.duikt.booking.service.ClassroomService;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
@Tag(name = "Classrooms", description = "Операції для роботи з навчальними аудиторіями")
public class ClassroomController {

    private final ClassroomService classroomService;

    @GetMapping
    @Operation(summary = "Отримати список усіх аудиторій")
    public List<Classroom> getAllClassrooms() {
        return classroomService.getAllClassrooms();
    }

    @GetMapping("/active")
    @Operation(summary = "Отримати список активних аудиторій")
    public List<Classroom> getActiveClassrooms() {
        return classroomService.getActiveClassrooms();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати детальну інформацію про аудиторію")
    public ClassroomDetailsDto getClassroomById(@PathVariable Long id) {
        return classroomService.getClassroomDetails(id);
    }

    @GetMapping("/available")
    @Operation(summary = "Отримати список доступних аудиторій на вказаний час")
    public List<ClassroomDetailsDto> getAvailableClassrooms(
            @Parameter(description = "Дата бронювання")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,

            @Parameter(description = "Час початку")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,

            @Parameter(description = "Час завершення")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime
    ) {
        return classroomService.getAvailableClassrooms(date, startTime, endTime);
    }

    @GetMapping("/search")
    @Operation(summary = "Пошук підходящих аудиторій за параметрами")
    public List<ClassroomDetailsDto> searchClassrooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String classroomTypeName,
            @RequestParam(required = false) String equipment
    ) {
        List<String> equipmentNames = (equipment == null || equipment.isBlank())
                ? List.of()
                : Arrays.stream(equipment.split(","))
                  .map(String::trim)
                  .filter(value -> !value.isBlank())
                  .toList();

        return classroomService.searchClassrooms(
                date,
                startTime,
                endTime,
                minCapacity,
                classroomTypeName,
                equipmentNames
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити нову аудиторію")
    public Classroom createClassroom(@RequestBody ClassroomRequestDto classroomRequestDto) {
        return classroomService.createClassroom(classroomRequestDto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Оновити дані аудиторії")
    public Classroom updateClassroom(@PathVariable Long id, @RequestBody ClassroomRequestDto classroomRequestDto) {
        return classroomService.updateClassroom(id, classroomRequestDto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалити аудиторію")
    public ResponseEntity<Void> deleteClassroom(@PathVariable Long id) {
        classroomService.deleteClassroom(id);
        return ResponseEntity.noContent().build();
    }
}
