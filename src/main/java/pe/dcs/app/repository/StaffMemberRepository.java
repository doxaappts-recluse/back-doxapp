package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.dcs.app.entity.StaffMember;

import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID>, JpaSpecificationExecutor<StaffMember> {
}
