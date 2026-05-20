package ua.edu.duikt.booking.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "classrooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "classroom_type_id", nullable = false)
    private ClassroomType classroomType;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer floor;

    @Column(length = 255)
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @JsonIgnore
    @Builder.Default
    @OneToMany(mappedBy = "classroom", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClassroomEquipment> classroomEquipmentList = new ArrayList<>();
}