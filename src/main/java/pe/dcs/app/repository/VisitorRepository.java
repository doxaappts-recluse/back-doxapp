package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Visitor;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VisitorRepository extends JpaRepository<Visitor, UUID>, JpaSpecificationExecutor<Visitor> {

    Optional<Visitor> findByPersonId(UUID personId);

    boolean existsByPersonId(UUID personId);
}
