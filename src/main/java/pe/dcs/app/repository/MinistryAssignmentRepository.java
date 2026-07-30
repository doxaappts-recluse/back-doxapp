package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.MinistryAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MinistryAssignmentRepository extends JpaRepository<MinistryAssignment, UUID> {

    List<MinistryAssignment> findByPersonIdOrderByStartDateDesc(
            UUID personId
    );

    boolean existsByPersonIdAndEndDateIsNull(
            UUID personId
    );

    Optional<MinistryAssignment> findFirstByPersonIdAndEndDateIsNullOrderByStartDateDesc(
            UUID personId
    );

}
