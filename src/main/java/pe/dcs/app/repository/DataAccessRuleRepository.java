package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.dcs.app.entity.DataAccessRule;

import java.util.Optional;
import java.util.UUID;

public interface DataAccessRuleRepository extends JpaRepository<DataAccessRule, UUID> {

    Optional<DataAccessRule> findByModule_CodeAndEnabledTrue(String moduleCode);
}
