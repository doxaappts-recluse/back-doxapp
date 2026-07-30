package pe.dcs.app.features.ministry_assignment.service;

import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentCreateRequest;
import pe.dcs.app.features.ministry_assignment.request.MinistryAssignmentUpdateRequest;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentGroupedResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryAssignmentResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistryRoleSimpleResponse;
import pe.dcs.app.features.ministry_assignment.response.MinistrySimpleResponse;

import java.util.List;
import java.util.UUID;

/**
 * Es un registro histórico: una persona puede servir muchas
 * veces en el mismo ministerio (varios periodos), pero esos
 * periodos no pueden solaparse en fechas. No hay restricción
 * entre ministerios distintos.
 */
public interface MinistryAssignmentService {

    MinistryAssignmentResponse create(UUID userId, MinistryAssignmentCreateRequest request);

    MinistryAssignmentResponse update(UUID userId, UUID assignmentId, MinistryAssignmentUpdateRequest request);

    void delete(UUID userId, UUID assignmentId);

    List<MinistryAssignmentGroupedResponse> getByUser(UUID userId);

    List<MinistrySimpleResponse> getAllMinistries();

    List<MinistryRoleSimpleResponse> getAllRoles();

}
