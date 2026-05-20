package ua.edu.duikt.booking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "classroom_equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClassroomEquipment {

    @EmbeddedId
    private ClassroomEquipmentId id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("classroomId")
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("equipmentId")
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false)
    private Integer quantity;
}