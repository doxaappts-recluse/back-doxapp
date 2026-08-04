package pe.dcs.app.features.smallgroup.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.SmallGroup;
import pe.dcs.app.entity.SmallGroupMember;
import pe.dcs.app.features.smallgroup.response.SmallGroupDetailResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupMemberResponse;
import pe.dcs.app.features.smallgroup.response.SmallGroupSearchRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;

import java.util.List;

@Component
public class SmallGroupMapper {

    public SmallGroupSearchRowResponse toSearchRow(
            SmallGroup group,
            long memberCount,
            boolean canManage,
            boolean showAudit
    ) {

        SmallGroupSearchRowResponse row = new SmallGroupSearchRowResponse();

        BaseMapper.mapAudit(group, row, showAudit);

        row.setId(group.getId());
        row.setName(group.getName());
        row.setLeaderName(resolveLeaderName(group));
        row.setMeetingDay(group.getMeetingDay());
        row.setMeetingTime(group.getMeetingTime());
        row.setLocation(group.getLocation());
        row.setCategory(group.getCategory());
        row.setStartDate(group.getStartDate());
        row.setEndDate(group.getEndDate());
        row.setStatus(group.getStatus());
        row.setMemberCount(memberCount);
        row.setCanManage(canManage);

        if (group.getBranch() != null) {
            row.setBranchId(group.getBranch().getId());
            row.setBranchName(group.getBranch().getName());
        }

        return row;
    }

    /**
     * leaderMember se calcula en el service (con
     * MembershipRepository) y se pasa acá ya resuelto — mismo
     * criterio que MarriageMapper.toDetailResponse.
     */
    public SmallGroupDetailResponse toDetailResponse(
            SmallGroup group,
            List<SmallGroupMemberResponse> members,
            boolean leaderMember,
            boolean canManage
    ) {

        SmallGroupDetailResponse response = new SmallGroupDetailResponse();

        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());

        response.setLeaderName(resolveLeaderName(group));
        response.setLeaderMember(leaderMember);

        if (group.getLeaderPerson() != null) {
            response.setLeaderPersonId(group.getLeaderPerson().getId());
            response.setLeaderDni(group.getLeaderPerson().getDni());
        }

        response.setMeetingDay(group.getMeetingDay());
        response.setMeetingTime(group.getMeetingTime());
        response.setLocation(group.getLocation());
        response.setCategory(group.getCategory());
        response.setStartDate(group.getStartDate());
        response.setEndDate(group.getEndDate());
        response.setTopic(group.getTopic());
        response.setStatus(group.getStatus());
        response.setCanManage(canManage);
        response.setMembers(members);

        if (group.getMinistryAssignment() != null) {
            response.setMinistryAssignmentId(group.getMinistryAssignment().getId());
        }

        if (group.getBranch() != null) {
            response.setBranchId(group.getBranch().getId());
            response.setBranchName(group.getBranch().getName());
        }

        return response;
    }

    /**
     * isMember se calcula en el service (con MembershipRepository)
     * y se pasa acá ya resuelto.
     */
    public SmallGroupMemberResponse toMemberResponse(
            SmallGroupMember member,
            boolean isMember
    ) {

        SmallGroupMemberResponse response = new SmallGroupMemberResponse();

        response.setId(member.getId());
        response.setGuestName(member.getGuestName());
        response.setGuestPhone(member.getGuestPhone());
        response.setJoinDate(member.getJoinDate());
        response.setStatus(member.getStatus());
        response.setMember(isMember);

        if (member.getPerson() != null) {
            response.setPersonId(member.getPerson().getId());
            response.setName(member.getPerson().getName() + " " + member.getPerson().getLastname());
            response.setDni(member.getPerson().getDni());
        } else {
            response.setName(member.getGuestName());
        }

        return response;
    }

    private String resolveLeaderName(SmallGroup group) {

        if (group.getLeaderPerson() != null) {
            return group.getLeaderPerson().getName()
                    + " "
                    + group.getLeaderPerson().getLastname();
        }

        return group.getLeaderName();
    }
}
