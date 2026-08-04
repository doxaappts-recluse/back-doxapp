package pe.dcs.app.features.church_attendance.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.ChurchService;
import pe.dcs.app.entity.ChurchServiceAttendance;
import pe.dcs.app.features.church_attendance.response.ChurchServiceAttendanceResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceDetailResponse;
import pe.dcs.app.features.church_attendance.response.ChurchServiceSearchRowResponse;
import pe.dcs.app.util.auditable.BaseMapper;

@Component
public class ChurchServiceMapper {

    public ChurchServiceSearchRowResponse toSearchRow(
            ChurchService churchService,
            boolean canManage,
            boolean showAudit
    ) {

        ChurchServiceSearchRowResponse row = new ChurchServiceSearchRowResponse();

        BaseMapper.mapAudit(churchService, row, showAudit);

        row.setId(churchService.getId());
        row.setName(churchService.getName());
        row.setDayOfWeek(churchService.getDayOfWeek());
        row.setTimeOfDay(churchService.getTimeOfDay());
        row.setStatus(churchService.getStatus());
        row.setCanManage(canManage);

        if (churchService.getBranch() != null) {
            row.setBranchId(churchService.getBranch().getId());
            row.setBranchName(churchService.getBranch().getName());
        }

        return row;
    }

    public ChurchServiceDetailResponse toDetailResponse(
            ChurchService churchService,
            boolean canManage
    ) {

        ChurchServiceDetailResponse response = new ChurchServiceDetailResponse();

        response.setId(churchService.getId());
        response.setName(churchService.getName());
        response.setDayOfWeek(churchService.getDayOfWeek());
        response.setTimeOfDay(churchService.getTimeOfDay());
        response.setStatus(churchService.getStatus());
        response.setCanManage(canManage);

        if (churchService.getBranch() != null) {
            response.setBranchId(churchService.getBranch().getId());
            response.setBranchName(churchService.getBranch().getName());
        }

        return response;
    }

    /**
     * isMember se calcula en el service (con MembershipRepository) y
     * se pasa acá ya resuelto — mismo criterio que
     * SmallGroupMapper.toMemberResponse.
     */
    public ChurchServiceAttendanceResponse toAttendanceResponse(
            ChurchServiceAttendance attendance,
            boolean isMember
    ) {

        ChurchServiceAttendanceResponse response = new ChurchServiceAttendanceResponse();

        response.setId(attendance.getId());
        response.setAttendanceDate(attendance.getAttendanceDate());
        response.setObservations(attendance.getObservations());
        response.setMember(isMember);

        response.setPersonId(attendance.getPerson().getId());
        response.setName(attendance.getPerson().getName() + " " + attendance.getPerson().getLastname());
        response.setDni(attendance.getPerson().getDni());

        return response;
    }
}
