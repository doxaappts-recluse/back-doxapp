package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.auth.domain.LoginEvent;
import pe.dcs.app.features.auth.domain.LoginResult;
import pe.dcs.app.features.auth.repo.LoginEventRepository;

import java.time.Clock;
import java.util.UUID;

/** Registro de eventos de autenticación (no lleva contraseñas ni tokens). Se une a la transacción del llamador. */
@Service
@RequiredArgsConstructor
public class LoginEventService {

    private final LoginEventRepository repository;
    private final Clock clock;

    public void log(LoginResult result, UUID credentialId, ActorType actorType, UUID organizationId,
                    String username, ClientInfo client, String detail) {
        LoginEvent e = new LoginEvent();
        e.setAt(clock.instant());
        e.setResult(result);
        e.setCredentialId(credentialId);
        e.setActorType(actorType);
        e.setOrganizationId(organizationId);
        e.setUsername(username == null ? null : username.substring(0, Math.min(username.length(), 160)));
        e.setIp(client == null ? null : client.ip());
        e.setUserAgent(client == null ? null : client.userAgent());
        e.setDetail(detail);
        repository.save(e);
    }
}
