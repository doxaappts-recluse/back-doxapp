package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.ChurchServiceAttendance;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChurchServiceAttendanceRepository extends JpaRepository<ChurchServiceAttendance, UUID> {

    List<ChurchServiceAttendance> findByChurchServiceIdAndAttendanceDateOrderByPerson_LastnameAsc(
            UUID churchServiceId,
            LocalDate attendanceDate
    );

    Optional<ChurchServiceAttendance> findByChurchServiceIdAndPersonIdAndAttendanceDate(
            UUID churchServiceId,
            UUID personId,
            LocalDate attendanceDate
    );

    boolean existsByChurchServiceIdAndPersonIdAndAttendanceDate(
            UUID churchServiceId,
            UUID personId,
            LocalDate attendanceDate
    );

    long countByChurchServiceIdAndAttendanceDate(UUID churchServiceId, LocalDate attendanceDate);
}
