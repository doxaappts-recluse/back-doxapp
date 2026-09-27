package pe.dcs.app.features.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SupportCaseRepository extends JpaRepository<SupportCase, UUID>, JpaSpecificationExecutor<SupportCase> {
}
