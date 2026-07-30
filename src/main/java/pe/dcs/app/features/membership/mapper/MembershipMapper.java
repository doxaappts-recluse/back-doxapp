package pe.dcs.app.features.membership.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Membership;
import pe.dcs.app.entity.Person;
import pe.dcs.app.features.membership.response.MembershipContextResponse;
import pe.dcs.app.features.membership.response.MembershipDetailResponse;
import pe.dcs.app.features.membership.response.MembershipSearchRowResponse;
import pe.dcs.app.features.membership.response.MembershipUserResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class MembershipMapper {

    public MembershipSearchRowResponse toSearchRow(Person person, Membership current, boolean showAudit) {

        MembershipSearchRowResponse row = new MembershipSearchRowResponse();

        BaseMapper.mapAudit(current, row, showAudit);

        row.setId(person.getId());
        row.setName(person.getName());
        row.setLastname(person.getLastname());
        row.setHasMembership(current != null);

        if (current != null) {

            row.setMembershipStatus(
                    current.getStatus() != null
                            ? current.getStatus()
                            : null
            );

            row.setMembershipReason(
                    current.getReason() != null
                            ? current.getReason().name()
                            : null
            );

            row.setMembershipStartDate(current.getStartDate());
            row.setMembershipEndDate(current.getEndDate());

            row.setMembershipExitReason(
                    current.getExitReason() != null
                            ? current.getExitReason().name()
                            : null
            );
        }

        return row;
    }

    public MembershipDetailResponse toDetailResponse(Membership membership) {

        MembershipDetailResponse response = new MembershipDetailResponse();

        response.setId(membership.getId());
        response.setStartDate(membership.getStartDate());
        response.setEndDate(membership.getEndDate());

        response.setStatus(
                membership.getStatus() != null
                        ? membership.getStatus().name()
                        : null
        );

        response.setReason(
                membership.getReason() != null
                        ? membership.getReason().name()
                        : null
        );

        response.setExitReason(
                membership.getExitReason() != null
                        ? membership.getExitReason().name()
                        : null
        );

        response.setNotes(membership.getNotes());
        response.setCurrent(Boolean.TRUE.equals(membership.getCurrent()));

        return response;
    }

    public MembershipUserResponse toUserResponse(Person person) {

        MembershipUserResponse response = new MembershipUserResponse();

        response.setId(person.getId());
        response.setName(person.getName());
        response.setLastname(person.getLastname());

        return response;
    }

    public MembershipContextResponse toContextResponse(Person person, Membership current) {

        MembershipContextResponse response = new MembershipContextResponse();

        response.setUser(toUserResponse(person));

        response.setCurrentMembership(
                current != null
                        ? toDetailResponse(current)
                        : null
        );

        return response;
    }
}
