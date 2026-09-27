package pe.dcs.app.features.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findByCaseIdOrderByCreatedAtAsc(UUID caseId);

    List<SupportMessage> findByCaseIdAndInternalFalseOrderByCreatedAtAsc(UUID caseId);
}
