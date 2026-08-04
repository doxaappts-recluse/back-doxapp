package pe.dcs.app.features.familygroup.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FamilyGroup;
import pe.dcs.app.entity.FamilyMember;
import pe.dcs.app.features.familygroup.response.FamilyGroupDetailResponse;
import pe.dcs.app.features.familygroup.response.FamilyGroupSearchRowResponse;
import pe.dcs.app.features.familygroup.response.FamilyMemberResponse;
import pe.dcs.app.util.auditable.BaseMapper;
import pe.dcs.app.util.enums.FamilyRole;

import java.util.List;
import java.util.Optional;

@Component
public class FamilyGroupMapper {

    public FamilyGroupSearchRowResponse toSearchRow(
            FamilyGroup group,
            long memberCount,
            boolean canManage,
            boolean showAudit
    ) {

        FamilyGroupSearchRowResponse row = new FamilyGroupSearchRowResponse();

        BaseMapper.mapAudit(group, row, showAudit);

        row.setId(group.getId());
        row.setName(group.getName());
        row.setStatus(group.getStatus());
        row.setMemberCount(memberCount);
        row.setCanManage(canManage);
        row.setHeadName(resolveHeadName(group));

        if (group.getBranch() != null) {
            row.setBranchId(group.getBranch().getId());
            row.setBranchName(group.getBranch().getName());
        }

        return row;
    }

    public FamilyGroupDetailResponse toDetailResponse(
            FamilyGroup group,
            List<FamilyMemberResponse> members,
            boolean canManage
    ) {

        FamilyGroupDetailResponse response = new FamilyGroupDetailResponse();

        response.setId(group.getId());
        response.setName(group.getName());
        response.setObservations(group.getObservations());
        response.setStatus(group.getStatus());
        response.setCanManage(canManage);
        response.setMembers(members);

        if (group.getBranch() != null) {
            response.setBranchId(group.getBranch().getId());
            response.setBranchName(group.getBranch().getName());
        }

        return response;
    }

    /**
     * isMember se calcula en el service (con MembershipRepository) y
     * se pasa acá ya resuelto — mismo criterio que
     * SmallGroupMapper.toMemberResponse.
     */
    public FamilyMemberResponse toMemberResponse(
            FamilyMember member,
            boolean isMember
    ) {

        FamilyMemberResponse response = new FamilyMemberResponse();

        response.setId(member.getId());
        response.setRole(member.getRole());
        response.setJoinDate(member.getJoinDate());
        response.setMember(isMember);

        response.setPersonId(member.getPerson().getId());
        response.setName(member.getPerson().getName() + " " + member.getPerson().getLastname());
        response.setDni(member.getPerson().getDni());

        return response;
    }

    private String resolveHeadName(FamilyGroup group) {

        Optional<FamilyMember> head =
                group.getMembers()
                        .stream()
                        .filter(m -> m.getRole() == FamilyRole.HEAD_OF_HOUSEHOLD)
                        .findFirst();

        return head.map(m -> m.getPerson().getName() + " " + m.getPerson().getLastname())
                .orElse(null);
    }
}
