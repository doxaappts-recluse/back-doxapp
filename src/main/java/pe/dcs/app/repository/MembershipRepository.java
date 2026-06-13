package pe.dcs.app.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.util.enums.membership.MembershipStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    Optional<Membership> findByUserIdAndCurrentTrue(
            UUID userId
    );

    List<Membership> findByUserIdOrderByStartDateDesc(
            UUID userId
    );

    Page<Membership> findByUserId(
            UUID userId,
            Pageable pageable
    );

    boolean existsByUserIdAndCurrentTrueAndStatus(
            UUID userId,
            MembershipStatus status
    );

}
