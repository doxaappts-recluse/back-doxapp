package pe.dcs.app.security.authz;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.security.jwt.JwtAuthentication;
import pe.dcs.app.util.Exceptions;

/** Núcleo 01 §2 · construye el {@link AccessScope} desde el token. El personal de plataforma NUNCA tiene alcance de tenant (D3). */
@Component
public class AccessScopeResolver {

    /** Actor autenticado de la petición actual (401 si no hay). */
    public AuthenticatedActor actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthentication jwt) {
            return jwt.getPrincipal();
        }
        throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
    }

    /**
     * Alcance de datos de una organización. Staff → 403 (no existe bypass de plataforma) — excepto en modo asistido
     * (M22, D3), donde {@code @ModuleAccess} ya validó el módulo exacto contra un grant activo: el alcance que se
     * arma aquí es de todas las sedes (los módulos de configuración del alcance asistido no son datos por sede).
     */
    public AccessScope current() {
        AuthenticatedActor a = actor();
        if (a.tokenType() != TokenType.ACCESS) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        if (a.isStaff() && a.isAssisted() && a.organizationId() != null) {
            return new AccessScope(a.organizationId(), true, a.branchIds(), a.ownerId(), a.contextId(), a.role());
        }
        if (a.isStaff() || a.organizationId() == null) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        return new AccessScope(a.organizationId(), a.allBranches(), a.branchIds(), a.ownerId(), a.contextId(), a.role());
    }
}
