package ua.edu.duikt.booking.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);
        return buildResponse(HttpStatus.CONFLICT, "Операцію неможливо виконати через конфлікт даних");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleInvalidRequest(Exception ex) {
        log.warn("Invalid request", ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "Некоректний запит");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled server error", ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Внутрішня помилка сервера");
    }

    private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message) {
        ApiError apiError = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                message,
                LocalDateTime.now()
        );
        return ResponseEntity.status(status).body(apiError);
    }
}
