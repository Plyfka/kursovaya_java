package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.duikt.booking.entity.Equipment;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    Optional<Equipment> findByNameIgnoreCase(String name);
}
