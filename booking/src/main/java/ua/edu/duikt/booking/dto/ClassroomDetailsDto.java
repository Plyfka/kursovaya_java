package ua.edu.duikt.booking.dto;

import java.util.List;

public record ClassroomDetailsDto(
        Long id,
        String name,
        Integer capacity,
        Integer floor,
        String description,
        Boolean isActive,
        Long buildingId,
        String buildingName,
        Long classroomTypeId,
        String classroomTypeName,
        List<EquipmentItemDto> equipment
) {
}