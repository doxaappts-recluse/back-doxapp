package pe.dcs.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.PrayerRequest;

import java.util.UUID;

@Repository
public interface PrayerRequestRepository extends JpaRepository<PrayerRequest, UUID> {

    Page<PrayerRequest> findByPersonIdOrderByRequestDateDesc(
            UUID personId,
            Pageable pageable
    );
}
