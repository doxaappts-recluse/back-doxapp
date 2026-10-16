package pe.dcs.app.security.jwt;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pe.dcs.app.security.AuthenticatedActor;

import java.util.List;

/**
 * Autenticación derivada del JWT. Authorities: SCOPE_&lt;TIPO_DE_TOKEN&gt; (restringe qué endpoints acepta cada tipo de token)
 * y ROLE_&lt;rol&gt; solo para tokens ACCESS.
 */
public class JwtAuthentication extends AbstractAuthenticationToken {

    private final AuthenticatedActor actor;

    public JwtAuthentication(AuthenticatedActor actor) {
        super(authoritiesOf(actor));
        this.actor = actor;
        setAuthenticated(true);
    }

    private static List<SimpleGrantedAuthority> authoritiesOf(AuthenticatedActor a) {
        var scope = new SimpleGrantedAuthority("SCOPE_" + a.tokenType().name());
        return a.tokenType() == pe.dcs.app.security.TokenType.ACCESS
                ? List.of(scope, new SimpleGrantedAuthority("ROLE_" + a.role().name()))
                : List.of(scope);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public AuthenticatedActor getPrincipal() {
        return actor;
    }

    @Override
    public String getName() {
        return actor.credentialId().toString();
    }
}
