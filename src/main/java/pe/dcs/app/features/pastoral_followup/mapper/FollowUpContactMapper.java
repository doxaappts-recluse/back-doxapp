package pe.dcs.app.features.pastoral_followup.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.FollowUpContact;
import pe.dcs.app.features.pastoral_followup.response.FollowUpContactResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class FollowUpContactMapper {

    public FollowUpContactResponse toResponse(
            FollowUpContact contact,
            boolean showAudit
    ) {

        FollowUpContactResponse response = new FollowUpContactResponse();

        BaseMapper.mapAudit(contact, response, showAudit);

        response.setId(contact.getId());
        response.setPersonId(contact.getPerson().getId());
        response.setContactDate(contact.getContactDate());
        response.setContactMethod(contact.getContactMethod());
        response.setResult(contact.getResult());
        response.setNotes(contact.getNotes());

        if (contact.getBranch() != null) {
            response.setBranchId(contact.getBranch().getId());
            response.setBranchName(contact.getBranch().getName());
        }

        return response;
    }
}
