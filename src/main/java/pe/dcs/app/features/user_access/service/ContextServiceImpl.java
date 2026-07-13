package pe.dcs.app.features.user_access.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.entity.Branch;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.features.user_access.response.ContextBranchResponse;
import pe.dcs.app.repository.UserAccessRepository;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContextServiceImpl implements ContextService {

    private final UserAccessRepository userAccessRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContextBranchResponse> getAvailableContexts(UUID userId) {

        List<UserAccess> accesses =
                userAccessRepository
                        .findActiveAccessesByPerson(userId);

        List<ContextBranchResponse> contexts =
                new ArrayList<>();

        for(UserAccess access : accesses){

            String role =
                    access.getRole()
                            .getValue();

            /*
             * ORG_ADMIN
             *
             * Acceso completo a la organización
             * Se expanden todas las sedes
             */
            if(role.equals("ORG_ADMIN")){
                access.getOrganization()
                        .getBranches()
                        .stream()
                        .filter(branch ->
                                branch.getStatus()
                                        == StatusType.ACTIVE
                        )
                        .forEach(branch -> {
                            contexts.add(
                                    map(access, branch)
                            );
                        });
                continue;
            }

            /*
             * ORG_BRANCH_ADMIN
             * ORG_USER
             *
             * Solo su sede asignada
             */
            contexts.add(
                    map(access, access.getBranch())
            );

        }

        return contexts;
    }

    private ContextBranchResponse map(
            UserAccess access,
            Branch branch
    ){

        return new ContextBranchResponse(

                access.getOrganization()
                        .getId(),

                access.getOrganization()
                        .getName(),

                branch != null
                        ? branch.getId()
                        : null,

                branch != null
                        ? branch.getName()
                        : null,

                branch != null
                        ? branch.getCode()
                        : null,

                access.getRole()
                        .getValue()
        );

    }

}