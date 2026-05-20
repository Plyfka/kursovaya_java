package ua.edu.duikt.booking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.edu.duikt.booking.dto.UserRequestDto;
import ua.edu.duikt.booking.entity.User;
import ua.edu.duikt.booking.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Операції для роботи з користувачами")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Отримати список усіх користувачів")
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати користувача за id")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити нового користувача")
    public User createUser(@RequestBody UserRequestDto request) {
        return userService.createUser(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Оновити дані користувача")
    public User updateUser(@PathVariable Long id, @RequestBody UserRequestDto request) {
        return userService.updateUser(id, request);
    }

    @PatchMapping("/{id}/block")
    @Operation(summary = "Заблокувати користувача")
    public User blockUser(@PathVariable Long id) {
        return userService.blockUser(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Видалення користувача не реалізовано")
    public ResponseEntity<Void> deleteUserPlaceholder(@PathVariable Long id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
