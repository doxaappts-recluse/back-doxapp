package pe.dcs.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.dcs.app.entity.FamilyMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, UUID> {

    /**
     * Una Person pertenece a un solo grupo familiar a la vez (ver
     * constraint unique en FamilyMember.person) — por eso esto
     * devuelve a lo más un registro.
     */
    Optional<FamilyMember> findByPersonId(UUID personId);

    boolean existsByPersonId(UUID personId);

    List<FamilyMember> findByFamilyGroupIdOrderByRoleAsc(UUID familyGroupId);

    long countByFamilyGroupId(UUID familyGroupId);
}
