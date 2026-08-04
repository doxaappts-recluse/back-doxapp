package pe.dcs.app.features.pastoral_followup.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.PrayerRequest;
import pe.dcs.app.features.pastoral_followup.response.PrayerRequestResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class PrayerRequestMapper {

    public PrayerRequestResponse toResponse(
            PrayerRequest request,
            boolean showAudit
    ) {

        PrayerRequestResponse response = new PrayerRequestResponse();

        BaseMapper.mapAudit(request, response, showAudit);

        response.setId(request.getId());
        response.setPersonId(request.getPerson().getId());
        response.setRequestDate(request.getRequestDate());
        response.setDescription(request.getDescription());
        response.setStatus(request.getStatus());
        response.setConfidential(request.isConfidential());
        response.setAnsweredNotes(request.getAnsweredNotes());

        if (request.getBranch() != null) {
            response.setBranchId(request.getBranch().getId());
            response.setBranchName(request.getBranch().getName());
        }

        return response;
    }
}
