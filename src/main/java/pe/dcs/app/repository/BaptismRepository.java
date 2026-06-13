package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Baptism;

import java.util.UUID;

@Repository
public interface BaptismRepository extends JpaRepository<Baptism, UUID>, JpaSpecificationExecutor<Baptism> {

    boolean existsByUserId(UUID userId);

}