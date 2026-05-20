package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.duikt.booking.entity.Classroom;

import java.util.List;
import java.util.Optional;

public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    @Override
    @EntityGraph(attributePaths = {"building", "classroomType", "classroomEquipmentList", "classroomEquipmentList.equipment"})
    List<Classroom> findAll();

    @Override
    @EntityGraph(attributePaths = {"building", "classroomType", "classroomEquipmentList", "classroomEquipmentList.equipment"})
    Optional<Classroom> findById(Long id);

    @EntityGraph(attributePaths = {"building", "classroomType", "classroomEquipmentList", "classroomEquipmentList.equipment"})
    List<Classroom> findByIsActiveTrue();

    @EntityGraph(attributePaths = {"building", "classroomType", "classroomEquipmentList", "classroomEquipmentList.equipment"})
    List<Classroom> findByBuildingId(Long buildingId);

    @EntityGraph(attributePaths = {"building", "classroomType", "classroomEquipmentList", "classroomEquipmentList.equipment"})
    List<Classroom> findByClassroomTypeId(Long classroomTypeId);
}