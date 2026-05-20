package ua.edu.duikt.booking.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ua.edu.duikt.booking.entity.Reservation;
import ua.edu.duikt.booking.entity.ReservationStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Override
    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    List<Reservation> findAll();

    @Override
    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    Optional<Reservation> findById(Long id);

    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    List<Reservation> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    List<Reservation> findByClassroomId(Long classroomId);

    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    List<Reservation> findByReservationDate(LocalDate reservationDate);

    @Query("""
            select case when count(r) > 0 then true else false end
            from Reservation r
            where r.classroom.id = :classroomId
              and r.reservationDate = :reservationDate
              and r.status = :status
              and r.startTime < :endTime
              and r.endTime > :startTime
            """)
    boolean existsConflict(@Param("classroomId") Long classroomId,
                           @Param("reservationDate") LocalDate reservationDate,
                           @Param("startTime") LocalTime startTime,
                           @Param("endTime") LocalTime endTime,
                           @Param("status") ReservationStatus status);

    @EntityGraph(attributePaths = {"user", "classroom", "classroom.building", "classroom.classroomType"})
    @Query("""
            select r
            from Reservation r
            where r.user.id = :userId
              and r.status = :status
              and (r.reservationDate > :today
                   or (r.reservationDate = :today and r.endTime > :currentTime))
            """)
    List<Reservation> findActiveAndFutureReservationsByUserId(@Param("userId") Long userId,
                                                              @Param("today") LocalDate today,
                                                              @Param("currentTime") LocalTime currentTime,
                                                              @Param("status") ReservationStatus status);

    void deleteByClassroomId(Long classroomId);
}
