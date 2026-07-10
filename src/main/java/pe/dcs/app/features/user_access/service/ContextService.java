package pe.dcs.app.features.user_access.service;

import pe.dcs.app.features.user_access.response.ContextBranchResponse;

import java.util.List;
import java.util.UUID;

public interface ContextService {

    List<ContextBranchResponse> getAvailableContexts(
            UUID userId
    );

}