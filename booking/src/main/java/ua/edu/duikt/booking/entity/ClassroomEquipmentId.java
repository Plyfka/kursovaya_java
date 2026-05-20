package ua.edu.duikt.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ClassroomEquipmentId implements Serializable {

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;
}
