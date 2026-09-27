package pe.dcs.app.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.ContractState;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.util.enums.RoleType;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Emisión y validación de JWT (HS256). Access 15 min; los tokens intermedios (PRE_AUTH/SETUP/CONTEXT) duran
 * {@code app.jwt.pre-auth-minutes}. El secreto es obligatorio y de al menos 32 bytes: la app no arranca sin él.
 */
@Slf4j
@Component
public class JwtService {

    static final String ISSUER = "doxapp";
    static final int TOKEN_FORMAT_VERSION = 1;

    private final String secret;
    private final long accessMinutes;
    private final long preAuthMinutes;
    private final Clock clock;
    private SecretKey key;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.access-minutes:15}") long accessMinutes,
                      @Value("${app.jwt.pre-auth-minutes:10}") long preAuthMinutes,
                      Clock clock) {
        this.secret = secret;
        this.accessMinutes = accessMinutes;
        this.preAuthMinutes = preAuthMinutes;
        this.clock = clock;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET (app.jwt.secret) es obligatorio y debe tener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Token emitido junto con sus fechas, para devolverlas al cliente. */
    public record IssuedToken(String token, Instant issuedAt, Instant expiresAt) {
    }

    public IssuedToken issue(AuthenticatedActor actor) {
        Duration ttl = Duration.ofMinutes(actor.tokenType() == TokenType.ACCESS ? accessMinutes : preAuthMinutes);
        Instant now = clock.instant();
        Instant exp = now.plus(ttl);

        var builder = Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(actor.credentialId().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("typ", actor.tokenType().name())
                .claim("act", actor.actorType().name())
                .claim("own", actor.ownerId().toString())
                .claim("role", actor.role().name())
                .claim("cs", actor.contractState().name())
                .claim("ver", TOKEN_FORMAT_VERSION);
        if (actor.contextId() != null) {
            builder.claim("ctx", actor.contextId().toString());
        }
        if (actor.organizationId() != null) {
            builder.claim("org", actor.organizationId().toString());
        }
        if (actor.activeBranchId() != null) {
            builder.claim("wbr", actor.activeBranchId().toString());
        }
        if (actor.assistedGrantId() != null) {
            builder.claim("ag", actor.assistedGrantId().toString());
        }
        builder.claim("br", actor.branchIds().stream().map(UUID::toString).sorted().toList());
        return new IssuedToken(builder.signWith(key, SignatureAlgorithm.HS256).compact(), now, exp);
    }

    /**
     * M22 (D3, acceso asistido): variante de {@link #issue(AuthenticatedActor)} con vencimiento propio, acotado al
     * del grant (nunca los 15 min fijos de un ACCESS normal) — la revocación real no depende de este vencimiento:
     * {@code AuthorizationService} vuelve a comprobar el grant en vivo en cada permiso, así que un vencimiento más
     * largo no debilita la revocación inmediata, solo evita que el staff tenga que "entrar" de nuevo cada 15 min.
     */
    public IssuedToken issueUntil(AuthenticatedActor actor, Instant expiresAt) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(actor.credentialId().toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiresAt))
                .claim("typ", actor.tokenType().name())
                .claim("act", actor.actorType().name())
                .claim("own", actor.ownerId().toString())
                .claim("role", actor.role().name())
                .claim("cs", actor.contractState().name())
                .claim("ver", TOKEN_FORMAT_VERSION);
        if (actor.contextId() != null) {
            builder.claim("ctx", actor.contextId().toString());
        }
        if (actor.organizationId() != null) {
            builder.claim("org", actor.organizationId().toString());
        }
        if (actor.activeBranchId() != null) {
            builder.claim("wbr", actor.activeBranchId().toString());
        }
        if (actor.assistedGrantId() != null) {
            builder.claim("ag", actor.assistedGrantId().toString());
        }
        builder.claim("br", actor.branchIds().stream().map(UUID::toString).sorted().toList());
        return new IssuedToken(builder.signWith(key, SignatureAlgorithm.HS256).compact(), now, expiresAt);
    }

    /** Resultado de validar un token: el actor, o el motivo del rechazo. */
    public record Parsed(AuthenticatedActor actor, boolean expired) {
        public boolean valid() {
            return actor != null;
        }
    }

    public Parsed parse(String token) {
        try {
            Claims c = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(ISSUER)
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            @SuppressWarnings("unchecked")
            List<String> br = c.get("br", List.class);
            Set<UUID> branches = br == null ? Set.of()
                    : br.stream().map(UUID::fromString).collect(Collectors.toUnmodifiableSet());

            AuthenticatedActor actor = new AuthenticatedActor(
                    UUID.fromString(c.getSubject()),
                    ActorType.valueOf(c.get("act", String.class)),
                    UUID.fromString(c.get("own", String.class)),
                    optUuid(c.get("ctx", String.class)),
                    optUuid(c.get("org", String.class)),
                    branches,
                    optUuid(c.get("wbr", String.class)),
                    RoleType.valueOf(c.get("role", String.class)),
                    ContractState.valueOf(c.get("cs", String.class)),
                    TokenType.valueOf(c.get("typ", String.class)),
                    optUuid(c.get("ag", String.class)));
            return new Parsed(actor, false);
        } catch (ExpiredJwtException e) {
            return new Parsed(null, true);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rechazado: {}", e.getMessage());
            return new Parsed(null, false);
        }
    }

    private static UUID optUuid(String v) {
        return v == null ? null : UUID.fromString(v);
    }
}
