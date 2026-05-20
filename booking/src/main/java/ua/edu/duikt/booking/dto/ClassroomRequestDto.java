package ua.edu.duikt.booking.dto;

public record ClassroomRequestDto(
        String name,
        Long buildingId,
        Long classroomTypeId,
        Integer capacity,
        Integer floor,
        String description,
        Boolean isActive
) {
}
