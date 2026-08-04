package pe.dcs.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FollowUpContact;

import java.util.UUID;

@Repository
public interface FollowUpContactRepository extends JpaRepository<FollowUpContact, UUID> {

    Page<FollowUpContact> findByPersonIdOrderByContactDateDesc(
            UUID personId,
            Pageable pageable
    );
}
