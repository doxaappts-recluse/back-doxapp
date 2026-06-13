package pe.dcs.app.features.user.ministry_user.service;

import pe.dcs.app.features.user.ministry_user.request.MinistryAssignmentRequest;
import pe.dcs.app.features.user.ministry_user.response.MinistryAssignmentResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryGroupedResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryOptionResponse;
import pe.dcs.app.features.user.ministry_user.response.MinistryRoleOptionResponse;

import java.util.List;
import java.util.UUID;

public interface MinistryAssignmentService {

    MinistryAssignmentResponse create(
            UUID userId,
            MinistryAssignmentRequest request
    );

    MinistryAssignmentResponse update(
            UUID userId,
            UUID assignmentId,
            MinistryAssignmentRequest request
    );

    void delete(
            UUID userId,
            UUID assignmentId
    );

    List<MinistryGroupedResponse> getServices(
            UUID userId
    );

    List<MinistryOptionResponse> getMinistries();

    List<MinistryRoleOptionResponse> getRoles();
}