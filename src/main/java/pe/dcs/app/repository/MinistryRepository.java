package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Ministry;

import java.util.List;
import java.util.UUID;

@Repository
public interface MinistryRepository extends JpaRepository<Ministry, UUID>, JpaSpecificationExecutor<Ministry> {
    List<Ministry> findAllByActiveTrueOrderByNameAsc();

    List<Ministry> findByActiveTrueOrderByNameAsc();
}