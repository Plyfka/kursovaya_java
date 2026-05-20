package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ua.edu.duikt.booking.entity.ClassroomType;

import java.util.Optional;

public interface ClassroomTypeRepository extends JpaRepository<ClassroomType, Long> {

    Optional<ClassroomType> findByNameIgnoreCase(String name);
}
