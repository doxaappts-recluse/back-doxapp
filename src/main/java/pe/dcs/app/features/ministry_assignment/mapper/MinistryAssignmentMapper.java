package pe.dcs.app.features.ministry_assignment.mapper;

import org.springframework.stereotype.Component;
import pe.dcs.app.entity.Ministry;
import pe.dcs.app.entity.MinistryAssignment;
import pe.dcs.app.entity.MinistryRole;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentGroupedItemResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentRefResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryRoleSimpleResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistrySimpleResponse;

@Component
public class MinistryAssignmentMapper {

    public MinistryAssignmentResponse toResponse(MinistryAssignment assignment) {

        MinistryAssignmentResponse response = new MinistryAssignmentResponse();

        response.setAssignmentId(assignment.getId());

        response.setMinistry(
                new MinistryAssignmentRefResponse(
                        assignment.getMinistry().getId(),
                        assignment.getMinistry().getName()
                )
        );

        response.setRole(
                new MinistryAssignmentRefResponse(
                        assignment.getMinistryRole().getId(),
                        assignment.getMinistryRole().getName()
                )
        );

        response.setStartDate(assignment.getStartDate());
        response.setEndDate(assignment.getEndDate());
        response.setReason(assignment.getReason());
        response.setObservation(assignment.getObservation());
        response.setCurrent(assignment.getEndDate() == null);

        return response;
    }

    public MinistryAssignmentGroupedItemResponse toGroupedItem(MinistryAssignment assignment) {

        MinistryAssignmentGroupedItemResponse item = new MinistryAssignmentGroupedItemResponse();

        item.setAssignmentId(assignment.getId());
        item.setRole(assignment.getMinistryRole().getName());
        item.setStartDate(assignment.getStartDate());
        item.setEndDate(assignment.getEndDate());
        item.setReason(assignment.getReason());
        item.setObservation(assignment.getObservation());
        item.setCurrent(assignment.getEndDate() == null);

        return item;
    }

    public MinistrySimpleResponse toMinistrySimple(Ministry ministry) {
        return new MinistrySimpleResponse(ministry.getId(), ministry.getName());
    }

    public MinistryRoleSimpleResponse toRoleSimple(MinistryRole role) {
        return new MinistryRoleSimpleResponse(
                role.getId(),
                role.getName(),
                role.getMinistry() != null ? role.getMinistry().getId() : null
        );
    }

}
