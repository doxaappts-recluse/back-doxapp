package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FamilyGroup;

import java.util.UUID;

@Repository
public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, UUID>, JpaSpecificationExecutor<FamilyGroup> {
}
