package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.dcs.app.entity.PersonBranch;
import pe.dcs.app.util.enums.StatusType;

import java.util.Optional;
import java.util.UUID;

public interface PersonBranchRepository extends JpaRepository<PersonBranch, UUID> {

    Optional<PersonBranch> findByPersonIdAndStatus(
            UUID personId,
            StatusType status
    );

}