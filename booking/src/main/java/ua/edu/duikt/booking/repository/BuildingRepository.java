package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.duikt.booking.entity.Building;

import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {

    Optional<Building> findByNameIgnoreCase(String name);
}
