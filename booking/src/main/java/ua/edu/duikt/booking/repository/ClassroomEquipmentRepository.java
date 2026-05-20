package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.duikt.booking.entity.ClassroomEquipment;
import ua.edu.duikt.booking.entity.ClassroomEquipmentId;

import java.util.List;

public interface ClassroomEquipmentRepository extends JpaRepository<ClassroomEquipment, ClassroomEquipmentId> {

    List<ClassroomEquipment> findByClassroomId(Long classroomId);

    List<ClassroomEquipment> findByEquipmentId(Long equipmentId);
}
