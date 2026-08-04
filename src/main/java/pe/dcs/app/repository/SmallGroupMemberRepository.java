package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.SmallGroupMember;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

@Repository
public interface SmallGroupMemberRepository extends JpaRepository<SmallGroupMember, UUID> {

    List<SmallGroupMember> findByGroupIdOrderByJoinDateAsc(UUID groupId);

    boolean existsByGroupIdAndPersonIdAndStatus(UUID groupId, UUID personId, StatusType status);

    long countByGroupIdAndStatus(UUID groupId, StatusType status);
}
