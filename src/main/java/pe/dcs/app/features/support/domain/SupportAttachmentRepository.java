package pe.dcs.app.features.support.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportAttachmentRepository extends JpaRepository<SupportAttachment, UUID> {

    List<SupportAttachment> findByMessageIdIn(Collection<UUID> messageIds);

    Optional<SupportAttachment> findByIdAndCaseId(UUID id, UUID caseId);
}
