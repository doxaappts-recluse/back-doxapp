package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Marriage;

import java.util.UUID;

@Repository
public interface MarriageRepository extends JpaRepository<Marriage, UUID>, JpaSpecificationExecutor<Marriage> {
}
