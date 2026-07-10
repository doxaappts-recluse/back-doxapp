package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.security.jwt.JwtProvider;
import pe.dcs.app.security.service.credentials.CredentialDetailsImpl;
import pe.dcs.app.util.Exceptions;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider jwtProvider;

    @Transactional(readOnly = true)
    public String changeContext(
            CredentialDetailsImpl user,
            UUID organizationId,
            UUID branchId
    ) {

        if (user.isSystem()) {

            return jwtProvider.generateContextToken(
                    user,
                    null,
                    null
            );
        }

        if (!user.hasOrganization(organizationId)) {
            throw new Exceptions(
                    "Organization access denied",
                    HttpStatus.FORBIDDEN
            );
        }

        if (branchId != null
                &&
                !user.hasOrganizationAdminAccess(organizationId)
                &&
                !user.hasBranch(organizationId, branchId)) {

            throw new Exceptions(
                    "Branch access denied",
                    HttpStatus.FORBIDDEN
            );
        }

        return jwtProvider.generateContextToken(
                user,
                organizationId,
                branchId
        );
    }

}