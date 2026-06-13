package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.EventAttendance;

import java.util.UUID;

@Repository
public interface EventAttendanceRepository extends JpaRepository<EventAttendance, UUID>, JpaSpecificationExecutor<EventAttendance> {

    boolean existsByRegistration_Id(UUID registrationId);

    @Query("""
        SELECT COUNT(a) > 0
        FROM EventAttendance a
        WHERE a.registration.id = :registrationId
    """)
    boolean existsByRegistrationId(UUID registrationId);

}