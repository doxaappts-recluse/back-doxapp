package pe.dcs.app.shared.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.jwt.JwtAuthentication;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Núcleo 01 §2 · AuditService.record(actor, org, módulo, acción, entidad, id, diff, ip, ua).
 * Se ejecuta en la misma transacción que el cambio (si el cambio falla, no queda evento huérfano).
 * El diff NUNCA incluye campos sensibles: se eliminan por nombre antes de guardar.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    /** Claves (en minúsculas) que jamás se persisten en un diff. */
    static final Set<String> SENSITIVE = Set.of(
            "password", "passwordhash", "password_hash", "newpassword", "currentpassword", "token", "refreshtoken",
            "accesstoken", "secret", "mfasecret", "mfa_secret_enc", "mfasecretenc", "code", "otp", "tokenhash", "token_hash");

    private final AuditEventRepository repository;
    private final Clock clock;

    /** Datos explícitos del evento. Los campos nulos se completan con el actor/petición actuales. */
    public record Command(String moduleCode, String action, String entityType, Object entityId,
                          UUID organizationId, UUID branchId, Map<String, Object> diff) {
        public static Command of(String moduleCode, String action, String entityType, Object entityId, Map<String, Object> diff) {
            return new Command(moduleCode, action, entityType, entityId, null, null, diff);
        }
    }

    public AuditEvent record(Command cmd) {
        AuditEvent e = new AuditEvent();
        e.setAt(clock.instant());
        e.setModuleCode(cmd.moduleCode());
        e.setAction(cmd.action());
        e.setEntityType(cmd.entityType());
        e.setEntityId(cmd.entityId() == null ? null : cmd.entityId().toString());
        e.setDiff(sanitize(cmd.diff()));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwt) {
            AuthenticatedActor a = jwt.getPrincipal();
            e.setActorType(a.actorType().name());
            e.setActorId(a.ownerId());
            e.setActorRole(a.role().name());
            e.setOrganizationId(cmd.organizationId() != null ? cmd.organizationId() : a.organizationId());
            e.setBranchId(cmd.branchId());
            e.setAssistedGrantId(a.assistedGrantId());
        } else {
            e.setActorType("SYSTEM");
            e.setOrganizationId(cmd.organizationId());
            e.setBranchId(cmd.branchId());
        }

        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra instanceof ServletRequestAttributes sra) {
            e.setIp(sra.getRequest().getRemoteAddr());
            String ua = sra.getRequest().getHeader("User-Agent");
            e.setUserAgent(ua == null ? null : ua.substring(0, Math.min(ua.length(), 255)));
        }
        return repository.save(e);
    }

    /** Copia el diff sin claves sensibles (a cualquier profundidad de primer nivel de mapas anidados). */
    static Map<String, Object> sanitize(Map<String, Object> diff) {
        if (diff == null) {
            return null;
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        diff.forEach((k, v) -> {
            if (SENSITIVE.contains(k.toLowerCase())) {
                return;
            }
            if (v instanceof Map<?, ?> nested) {
                Map<String, Object> inner = new LinkedHashMap<>();
                nested.forEach((nk, nv) -> inner.put(String.valueOf(nk), nv));
                clean.put(k, sanitize(inner));
            } else {
                clean.put(k, v);
            }
        });
        return clean;
    }
}
