package pe.dcs.app.features.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.access.domain.AccessStatus;
import pe.dcs.app.features.access.domain.ActorType;
import pe.dcs.app.features.access.domain.Credential;
import pe.dcs.app.features.access.domain.PlatformStaff;
import pe.dcs.app.features.access.domain.UserAccess;
import pe.dcs.app.features.access.repo.CredentialRepository;
import pe.dcs.app.features.access.repo.PlatformStaffRepository;
import pe.dcs.app.features.access.repo.UserAccessRepository;
import pe.dcs.app.features.auth.domain.LoginResult;
import pe.dcs.app.features.auth.domain.RefreshToken;
import pe.dcs.app.features.auth.domain.TokenPurpose;
import pe.dcs.app.features.auth.dto.AcceptInviteRequest;
import pe.dcs.app.features.auth.dto.AuthResponse;
import pe.dcs.app.features.auth.dto.AuthStatus;
import pe.dcs.app.features.auth.dto.ChangePasswordRequest;
import pe.dcs.app.features.auth.dto.ContextInfo;
import pe.dcs.app.features.auth.dto.ContextRequest;
import pe.dcs.app.features.auth.dto.DisableMfaRequest;
import pe.dcs.app.features.auth.dto.ForgotPasswordRequest;
import pe.dcs.app.features.auth.dto.LoginRequest;
import pe.dcs.app.features.auth.dto.MeResponse;
import pe.dcs.app.features.auth.dto.MfaCodeRequest;
import pe.dcs.app.features.auth.dto.MfaSetupResponse;
import pe.dcs.app.features.auth.dto.OrganizationChoice;
import pe.dcs.app.features.auth.dto.ResetPasswordRequest;
import pe.dcs.app.features.auth.dto.SessionInfo;
import pe.dcs.app.features.auth.dto.SetupStep;
import pe.dcs.app.features.organization.domain.Organization;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.security.AuthenticatedActor;
import pe.dcs.app.security.ContractState;
import pe.dcs.app.security.TokenType;
import pe.dcs.app.security.authz.AccessStateCache;
import pe.dcs.app.security.jwt.JwtService;
import pe.dcs.app.shared.audit.AuditService;
import pe.dcs.app.util.Exceptions;
import pe.dcs.app.util.enums.RoleType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Casos de uso de autenticación (spec 00 §5, M05). Todo el flujo corre en UNA transacción que NO revierte con
 * {@link Exceptions}: los contadores de fallos, los bloqueos, la revocación por reúso de refresh y los eventos de
 * seguridad deben persistir justamente cuando la operación responde con error.
 */
@Service
@Transactional(noRollbackFor = Exceptions.class)
public class AuthenticationService {

    private static final String MODULE = "MY_ACCOUNT";
    /** Elección del selector del login directo para el personal de plataforma. */
    static final String PLATFORM_CHOICE = "@platform";

    private final CredentialRepository credentials;
    private final PlatformStaffRepository staffRepository;
    private final UserAccessRepository accessRepository;
    private final OrganizationRepository organizations;
    private final ContextService contexts;
    private final JwtService jwt;
    private final RefreshTokenService refresh;
    private final PasswordEncoder encoder;
    private final PasswordPolicy passwordPolicy;
    private final LoginRateLimiter limiter;
    private final LoginEventService events;
    private final OneTimeTokenService oneTime;
    private final InvitationService invitations;
    private final TotpService totp;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final AccessStateCache accessState;
    private final Clock clock;

    /** MFA obligatorio para el personal y exigido a quien lo tenga activo. Apagado por ahora (app.security.mfa-enforced). */
    private final boolean mfaEnforced;
    private final int maxFailedAttempts;
    private final Duration lockDuration;
    private final int idleMinutesPlatform;
    private final int idleMinutesOrg;

    /** Hash de relleno: se compara aunque el usuario no exista, para no revelar su existencia por el tiempo de respuesta. */
    private final String dummyHash;

    public AuthenticationService(CredentialRepository credentials, PlatformStaffRepository staffRepository,
                                 UserAccessRepository accessRepository, OrganizationRepository organizations,
                                 ContextService contexts, JwtService jwt, RefreshTokenService refresh,
                                 PasswordEncoder encoder, PasswordPolicy passwordPolicy, LoginRateLimiter limiter,
                                 LoginEventService events, OneTimeTokenService oneTime, InvitationService invitations,
                                 TotpService totp, SecretCipher cipher, AuditService audit,
                                 AccessStateCache accessState, Clock clock,
                                 @Value("${app.security.mfa-enforced:false}") boolean mfaEnforced,
                                 @Value("${app.security.max-failed-attempts:5}") int maxFailedAttempts,
                                 @Value("${app.security.lock-minutes:15}") long lockMinutes,
                                 @Value("${app.security.idle-minutes-platform:30}") int idleMinutesPlatform,
                                 @Value("${app.security.idle-minutes-org:480}") int idleMinutesOrg) {
        this.credentials = credentials;
        this.staffRepository = staffRepository;
        this.accessRepository = accessRepository;
        this.organizations = organizations;
        this.contexts = contexts;
        this.jwt = jwt;
        this.refresh = refresh;
        this.encoder = encoder;
        this.passwordPolicy = passwordPolicy;
        this.limiter = limiter;
        this.events = events;
        this.oneTime = oneTime;
        this.invitations = invitations;
        this.totp = totp;
        this.cipher = cipher;
        this.audit = audit;
        this.accessState = accessState;
        this.clock = clock;
        this.mfaEnforced = mfaEnforced;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockMinutes);
        this.idleMinutesPlatform = idleMinutesPlatform;
        this.idleMinutesOrg = idleMinutesOrg;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    // ------------------------------------------------------------------ login

    public AuthResponse loginPlatform(LoginRequest req, ClientInfo client) {
        String key = rateKey(client, "platform", req.username());
        limiter.assertAllowed(key);
        Credential c = credentials.findStaffByUsername(req.username().trim()).orElse(null);
        return authenticate(c, null, req, key, client);
    }

    public AuthResponse loginOrganization(String slug, LoginRequest req, ClientInfo client) {
        String key = rateKey(client, slug, req.username());
        limiter.assertAllowed(key);
        Organization org = organizations.findBySlug(slug).orElse(null);
        Credential c = org == null ? null : credentials.findPersonByUsername(org.getId(), req.username().trim()).orElse(null);
        return authenticate(c, org, req, key, client);
    }


    /**
     * Login directo: solo usuario y contraseña. Se buscan las credenciales con ese usuario en todas partes y se
     * comprueba la contraseña contra cada una. Si sirve en una sola, se sigue el flujo normal; si sirve en varias
     * (mismo usuario y contraseña en dos iglesias) se pide elegir una, ya con la contraseña validada. Los errores
     * siguen siendo genéricos: no se distingue usuario inexistente de contraseña errónea.
     */
    public AuthResponse login(LoginRequest req, ClientInfo client) {
        String username = req.username().trim();
        String choice = req.organizationSlug() == null || req.organizationSlug().isBlank() ? null : req.organizationSlug().trim();
        String key = rateKey(client, "direct", username); // sin la elección: rotarla no reinicia el límite
        limiter.assertAllowed(key);

        List<Credential> found = credentials.findAllByUsername(username);
        Map<UUID, Organization> orgs = organizations.findAllById(
                found.stream().map(Credential::getOrganizationId).filter(java.util.Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Organization::getId, Function.identity()));

        List<Credential> candidates = found.stream().filter(c -> choice == null
                || (PLATFORM_CHOICE.equals(choice) ? c.getOrganizationId() == null
                : c.getOrganizationId() != null && orgs.get(c.getOrganizationId()) != null
                && orgs.get(c.getOrganizationId()).getSlug().equalsIgnoreCase(choice))).toList();

        boolean anyHash = candidates.stream().anyMatch(c -> c.getPasswordHash() != null);
        if (!anyHash) {
            encoder.matches(req.password(), dummyHash); // mismo costo de tiempo que un usuario real
        }
        List<Credential> matched = candidates.stream()
                .filter(c -> c.getPasswordHash() != null && encoder.matches(req.password(), c.getPasswordHash())).toList();

        Instant now = clock.instant();
        if (matched.isEmpty()) {
            for (Credential c : candidates) {
                if (c.getStatus() == AccessStatus.ACTIVE && !c.isLockedNow(now)) {
                    registerFailure(c, now);
                }
            }
            Credential first = candidates.isEmpty() ? null : candidates.get(0);
            return failLogin(key, first, first == null ? null : first.getActorType(),
                    first == null ? null : first.getOrganizationId(), username, client,
                    first == null ? LoginResult.UNKNOWN_USER : LoginResult.BAD_PASSWORD);
        }

        List<Credential> usable = matched.stream().filter(c -> c.getStatus() == AccessStatus.ACTIVE && !c.isLockedNow(now)
                && (c.getOrganizationId() == null || orgs.get(c.getOrganizationId()).getStatus().allowsLogin())).toList();
        if (usable.size() > 1) {
            AuthResponse merged = mergedContexts(usable, key, client);
            if (merged != null) {
                return merged;
            }
            limiter.reset(key);
            List<OrganizationChoice> options = usable.stream()
                    .map(c -> c.getOrganizationId() == null ? new OrganizationChoice(PLATFORM_CHOICE, null)
                            : new OrganizationChoice(orgs.get(c.getOrganizationId()).getSlug(), orgs.get(c.getOrganizationId()).getName()))
                    .sorted(java.util.Comparator.comparing(OrganizationChoice::name, java.util.Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)))
                    .toList();
            return new AuthResponse(AuthStatus.ORGANIZATION_REQUIRED, null, null, null, null, null, null, null, null, null, options);
        }
        Credential chosen = usable.isEmpty() ? matched.get(0) : usable.get(0);
        return afterPassword(chosen, chosen.getOrganizationId() == null ? null : orgs.get(chosen.getOrganizationId()),
                username, key, client);
    }

    /**
     * Mismo usuario y contraseña en varias iglesias: en vez de un selector aparte se arma UNA sola pantalla de contexto
     * con todas las opciones (iglesia + sede + rol). El token de contexto es de la primera credencial y lleva las otras
     * como "hermanas" (claim br) para que {@link #selectContext} pueda elegir un acceso de cualquiera de ellas.
     * Devuelve null si no aplica (hay personal de plataforma, MFA o pasos obligatorios pendientes): entonces se usa el
     * selector de organización de siempre.
     */
    private AuthResponse mergedContexts(List<Credential> usable, String key, ClientInfo client) {
        boolean simple = usable.stream().allMatch(c -> c.getActorType() == ActorType.PERSON
                && !(mfaEnforced && c.isMfaEnabled()) && pendingSetup(c).isEmpty());
        if (!simple) {
            return null;
        }
        List<Credential> withContexts = new java.util.ArrayList<>();
        List<ContextInfo> all = new java.util.ArrayList<>();
        for (Credential c : usable) {
            List<ContextInfo> list = contexts.effectiveContexts(c);
            if (!list.isEmpty()) {
                withContexts.add(c);
                all.addAll(list);
            }
        }
        if (withContexts.isEmpty()) {
            return null;
        }
        limiter.reset(key);
        for (Credential c : withContexts) {
            if (c.getFailedAttempts() > 0) {
                c.setFailedAttempts(0);
                credentials.save(c);
            }
        }
        if (withContexts.size() == 1) {
            return proceed(withContexts.get(0), client);
        }
        all.sort(java.util.Comparator.comparing((ContextInfo i) -> i.organizationName().toLowerCase())
                .thenComparing(i -> i.branchName() == null ? "" : i.branchName().toLowerCase()));
        Credential primary = withContexts.get(0);
        java.util.Set<UUID> siblings = withContexts.stream().skip(1).map(Credential::getId).collect(Collectors.toSet());
        JwtService.IssuedToken t = jwt.issue(contexts.intermediateWithSiblings(primary, siblings));
        return intermediate(AuthStatus.CONTEXT_REQUIRED, t, null, all, contexts.userInfo(primary));
    }

    private AuthResponse authenticate(Credential c, Organization org, LoginRequest req, String key, ClientInfo client) {
        Instant now = clock.instant();
        boolean known = c != null && c.getPasswordHash() != null;
        boolean passwordOk = encoder.matches(req.password(), known ? c.getPasswordHash() : dummyHash) && known;

        if (c == null) {
            return failLogin(key, null, null, org == null ? null : org.getId(), req.username(), client, LoginResult.UNKNOWN_USER);
        }
        if (!passwordOk) {
            if (c.getStatus() == AccessStatus.ACTIVE && !c.isLockedNow(now)) {
                registerFailure(c, now);
            }
            return failLogin(key, c, c.getActorType(), c.getOrganizationId(), req.username(), client, LoginResult.BAD_PASSWORD);
        }
        return afterPassword(c, org, req.username(), key, client);
    }

    /** Contraseña correcta: ya no se revela nada que no sepa su dueño (bloqueo, baja, organización suspendida). */
    private AuthResponse afterPassword(Credential c, Organization org, String username, String key, ClientInfo client) {
        Instant now = clock.instant();
        if (c.isLockedNow(now)) {
            events.log(LoginResult.LOCKED, c.getId(), c.getActorType(), c.getOrganizationId(), username, client, null);
            long minutes = Math.max(1, Duration.between(now, c.getLockedUntil()).plusSeconds(59).toMinutes());
            throw new Exceptions("error.auth.accountLocked", HttpStatus.UNAUTHORIZED, minutes);
        }
        ContextService.Owner owner = contexts.owner(c);
        if (c.getStatus() != AccessStatus.ACTIVE || owner == null || !owner.active()) {
            events.log(LoginResult.INACTIVE, c.getId(), c.getActorType(), c.getOrganizationId(), username, client, null);
            throw new Exceptions("error.auth.accountInactive", HttpStatus.FORBIDDEN);
        }
        if (org != null && !org.getStatus().allowsLogin()) {
            events.log(LoginResult.ORG_BLOCKED, c.getId(), c.getActorType(), org.getId(), username, client, null);
            throw new Exceptions("error.org.suspended", HttpStatus.FORBIDDEN);
        }

        limiter.reset(key);
        if (c.getFailedAttempts() > 0) {
            c.setFailedAttempts(0);
            credentials.save(c);
        }
        if (mfaEnforced && c.isMfaEnabled()) {
            events.log(LoginResult.MFA_REQUIRED, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
            JwtService.IssuedToken t = jwt.issue(contexts.intermediate(c, TokenType.PRE_AUTH));
            return intermediate(AuthStatus.MFA_REQUIRED, t, null, null, null);
        }
        return proceed(c, client);
    }

    /** Anota el fallo y responde con el error genérico (no distingue usuario inexistente, contraseña mala, bloqueo ni baja). */
    private AuthResponse failLogin(String key, Credential c, ActorType actor, UUID orgId, String username,
                                   ClientInfo client, LoginResult result) {
        limiter.recordFailure(key);
        events.log(result, c == null ? null : c.getId(), actor, orgId, username, client, null);
        throw new Exceptions("error.auth.invalidCredentials", HttpStatus.UNAUTHORIZED);
    }

    /** +1 fallo; al llegar al máximo, bloqueo temporal de la credencial. */
    private void registerFailure(Credential c, Instant now) {
        int failures = c.getFailedAttempts() + 1;
        if (failures >= maxFailedAttempts) {
            c.setLockedUntil(now.plus(lockDuration));
            c.setFailedAttempts(0);
        } else {
            c.setFailedAttempts(failures);
        }
        credentials.save(c);
    }

    // ------------------------------------------------------------------ MFA

    public AuthResponse verifyMfa(MfaCodeRequest req, AuthenticatedActor actor, ClientInfo client) {
        Credential c = credential(actor);
        Instant now = clock.instant();
        String key = "mfa|" + c.getId();
        limiter.assertAllowed(key);
        if (c.isLockedNow(now) || c.getStatus() != AccessStatus.ACTIVE || !c.isMfaEnabled() || c.getMfaSecretEnc() == null) {
            throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
        }
        if (!totp.verify(cipher.decrypt(c.getMfaSecretEnc()), req.code())) {
            limiter.recordFailure(key);
            registerFailure(c, now);
            events.log(LoginResult.MFA_FAILED, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
            throw new Exceptions("error.auth.mfaInvalid", HttpStatus.BAD_REQUEST);
        }
        limiter.reset(key);
        c.setFailedAttempts(0);
        credentials.save(c);
        return proceed(c, client);
    }

    public MfaSetupResponse mfaSetup(AuthenticatedActor actor) {
        Credential c = credential(actor);
        if (c.isMfaEnabled()) {
            throw new Exceptions("error.auth.mfaAlreadyEnabled", HttpStatus.CONFLICT);
        }
        String secret = totp.newSecret();
        c.setMfaSecretEnc(cipher.encrypt(secret));
        credentials.save(c);
        return new MfaSetupResponse(secret, totp.otpAuthUri(c.getUsername(), secret));
    }

    /** Activa el MFA tras comprobar un código. Con token SETUP continúa el flujo; con ACCESS devuelve vacío. */
    public Optional<AuthResponse> mfaEnable(MfaCodeRequest req, AuthenticatedActor actor, ClientInfo client) {
        Credential c = credential(actor);
        if (c.isMfaEnabled()) {
            throw new Exceptions("error.auth.mfaAlreadyEnabled", HttpStatus.CONFLICT);
        }
        if (c.getMfaSecretEnc() == null) {
            throw new Exceptions("error.auth.mfaNotSetup", HttpStatus.BAD_REQUEST);
        }
        String key = "mfa|" + c.getId();
        limiter.assertAllowed(key);
        if (!totp.verify(cipher.decrypt(c.getMfaSecretEnc()), req.code())) {
            limiter.recordFailure(key);
            throw new Exceptions("error.auth.mfaInvalid", HttpStatus.BAD_REQUEST);
        }
        limiter.reset(key);
        c.setMfaEnabled(true);
        credentials.save(c);
        audit(actor, c, "MFA_ENABLED");
        return actor.tokenType() == TokenType.SETUP ? Optional.of(proceed(c, client)) : Optional.empty();
    }

    public void mfaDisable(DisableMfaRequest req, AuthenticatedActor actor) {
        Credential c = credential(actor);
        if (mfaEnforced && c.getActorType() == ActorType.STAFF) {
            throw new Exceptions("error.auth.mfaMandatory", HttpStatus.FORBIDDEN);
        }
        if (!c.isMfaEnabled()) {
            return;
        }
        String key = "mfa|" + c.getId();
        limiter.assertAllowed(key);
        if (!encoder.matches(req.password(), c.getPasswordHash())) {
            limiter.recordFailure(key);
            throw new Exceptions("error.auth.currentPasswordWrong", HttpStatus.BAD_REQUEST);
        }
        if (!totp.verify(cipher.decrypt(c.getMfaSecretEnc()), req.code())) {
            limiter.recordFailure(key);
            throw new Exceptions("error.auth.mfaInvalid", HttpStatus.BAD_REQUEST);
        }
        limiter.reset(key);
        c.setMfaEnabled(false);
        c.setMfaSecretEnc(null);
        credentials.save(c);
        audit(actor, c, "MFA_DISABLED");
    }

    // ------------------------------------------------------------------ contexto y sesión

    /** Elige (o cambia a) un contexto. Token CONTEXT (tras el login) o ACCESS (cambio desde la sesión abierta). */
    public AuthResponse selectContext(ContextRequest req, AuthenticatedActor actor, ClientInfo client) {
        Credential c = credential(actor);
        if (actor.tokenType() == TokenType.CONTEXT && !actor.branchIds().isEmpty()) {
            // pantalla de contexto con varias iglesias: el acceso elegido puede ser de una credencial hermana
            UUID person = contexts.personOfAccess(req.contextId()).orElseThrow(() -> new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN));
            Credential owner = credentials.findByPersonId(person).orElseThrow(() -> new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN));
            if (!owner.getId().equals(c.getId()) && !actor.branchIds().contains(owner.getId())) {
                throw new Exceptions("error.auth.contextInvalid", HttpStatus.FORBIDDEN);
            }
            c = owner;
        }
        if (c.getStatus() != AccessStatus.ACTIVE || c.isLockedNow(clock.instant())) {
            throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
        }
        if (c.getActorType() != ActorType.PERSON) {
            throw new Exceptions("error.common.forbidden", HttpStatus.FORBIDDEN);
        }
        ContextService.Resolved resolved = contexts.resolve(c, req.contextId(), req.branchId());
        if (actor.tokenType() == TokenType.ACCESS) {
            // cambio de contexto: la sesión anterior se cierra
            refresh.revokeByToken(req.refreshToken(), c.getId(), "CONTEXT_SWITCH");
        }
        return startSession(c, resolved, client);
    }

    public AuthResponse refresh(String rawRefreshToken, ClientInfo client) {
        RefreshTokenService.Rotation r = refresh.rotate(rawRefreshToken, client);
        if (r.status() == RefreshTokenService.RotationStatus.INVALID) {
            throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
        }
        Credential c = credentials.findById(r.credentialId()).orElse(null);
        if (r.status() == RefreshTokenService.RotationStatus.REUSED) {
            events.log(LoginResult.REFRESH_REUSE, r.credentialId(), c == null ? null : c.getActorType(),
                    c == null ? null : c.getOrganizationId(), c == null ? null : c.getUsername(), client, null);
            throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
        }
        try {
            if (c == null || c.getStatus() != AccessStatus.ACTIVE || c.isLockedNow(clock.instant())) {
                throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
            }
            ContextService.Resolved resolved = contexts.resolve(c, r.contextId(), r.branchId());
            JwtService.IssuedToken token = jwt.issue(resolved.actor());
            return authenticated(c, resolved, token, r.issued().rawToken());
        } catch (Exceptions e) {
            // acceso desactivado / organización suspendida / credencial bloqueada: la sesión deja de renovarse
            refresh.revokeFamily(r.familyId(), "ACCESS_LOST");
            throw new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED);
        }
    }

    public void logout(String rawRefreshToken) {
        refresh.revokeByToken(rawRefreshToken, null, "LOGOUT");
    }

    // ------------------------------------------------------------------ contraseñas e invitaciones

    /** Siempre responde igual exista o no el usuario (no revela cuentas). */
    public void forgotPassword(ForgotPasswordRequest req, ClientInfo client) {
        String key = "forgot|" + client.ip();
        limiter.assertAllowed(key);
        limiter.recordFailure(key);

        String slug = req.organizationSlug() == null || req.organizationSlug().isBlank() ? null : req.organizationSlug().trim();
        String username = req.username().trim();
        if (slug != null) {
            Organization org = organizations.findBySlug(slug).orElse(null);
            if (org != null) {
                credentials.findPersonByUsername(org.getId(), username).ifPresent(c -> sendReset(c, org));
            }
            return;
        }
        // sin organización: todas las cuentas con ese usuario (plataforma y organizaciones); cada aviso va al correo de su dueño
        for (Credential c : credentials.findAllByUsername(username)) {
            Organization org = c.getOrganizationId() == null ? null : organizations.findById(c.getOrganizationId()).orElse(null);
            if (c.getOrganizationId() == null || org != null) {
                sendReset(c, org);
            }
        }
    }

    private void sendReset(Credential c, Organization org) {
        if (c.getStatus() != AccessStatus.ACTIVE || c.getPasswordHash() == null || (org != null && !org.getStatus().allowsLogin())) {
            return;
        }
        ContextService.Owner owner = contexts.owner(c);
        if (owner == null || !owner.active() || owner.email() == null || owner.email().isBlank()) {
            return;
        }
        String locale = org != null ? org.getDefaultLanguage() : LocaleContextHolder.getLocale().getLanguage();
        invitations.sendPasswordReset(c.getId(), owner.email(), owner.name(), org == null ? null : org.getSlug(), locale);
    }

    public void resetPassword(ResetPasswordRequest req, ClientInfo client) {
        UUID credentialId = oneTime.peek(req.token(), TokenPurpose.RESET);
        Credential c = credentials.findById(credentialId)
                .filter(x -> x.getStatus() == AccessStatus.ACTIVE)
                .orElseThrow(() -> new Exceptions("error.auth.tokenInvalid", HttpStatus.BAD_REQUEST));
        passwordPolicy.assertValid(req.newPassword(), c.getUsername());
        oneTime.consume(req.token(), TokenPurpose.RESET);
        applyPassword(c, req.newPassword());
        c.setFailedAttempts(0);
        c.setLockedUntil(null);
        credentials.save(c);
        refresh.revokeAll(c.getId(), "PASSWORD_RESET");
        events.log(LoginResult.PASSWORD_RESET, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
        auditAnonymous(c, "PASSWORD_RESET");
    }

    /** Acepta la invitación: fija la contraseña, activa la credencial y los accesos pendientes. No inicia sesión. */
    public void acceptInvite(AcceptInviteRequest req, ClientInfo client) {
        UUID credentialId = oneTime.peek(req.token(), TokenPurpose.INVITE);
        Credential c = credentials.findById(credentialId)
                .filter(x -> x.getStatus() == AccessStatus.INVITED || x.getStatus() == AccessStatus.ACTIVE)
                .orElseThrow(() -> new Exceptions("error.auth.tokenInvalid", HttpStatus.BAD_REQUEST));
        passwordPolicy.assertValid(req.newPassword(), c.getUsername());
        oneTime.consume(req.token(), TokenPurpose.INVITE);

        applyPassword(c, req.newPassword());
        c.setStatus(AccessStatus.ACTIVE);
        credentials.save(c);

        if (c.getActorType() == ActorType.STAFF) {
            PlatformStaff s = staffRepository.findById(c.getStaffId()).orElse(null);
            if (s != null && s.getStatus() == AccessStatus.INVITED) {
                s.setStatus(AccessStatus.ACTIVE);
                staffRepository.save(s);
            }
        } else {
            for (UserAccess a : accessRepository.findByPersonIdAndOrganizationIdAndStatus(
                    c.getPersonId(), c.getOrganizationId(), AccessStatus.INVITED)) {
                a.setStatus(AccessStatus.ACTIVE);
                accessRepository.save(a);
                accessState.invalidate(a.getId());
            }
        }
        events.log(LoginResult.INVITE_ACCEPTED, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
        auditAnonymous(c, "INVITE_ACCEPTED");
    }

    /** Cambio de contraseña. Con token SETUP continúa el flujo; con ACCESS cierra todas las sesiones y devuelve vacío. */
    public Optional<AuthResponse> changePassword(ChangePasswordRequest req, AuthenticatedActor actor, ClientInfo client) {
        Credential c = credential(actor);
        String key = "chg|" + c.getId();
        limiter.assertAllowed(key);
        if (c.getPasswordHash() == null || !encoder.matches(req.currentPassword(), c.getPasswordHash())) {
            limiter.recordFailure(key);
            throw new Exceptions("error.auth.currentPasswordWrong", HttpStatus.BAD_REQUEST);
        }
        passwordPolicy.assertValid(req.newPassword(), c.getUsername());
        if (encoder.matches(req.newPassword(), c.getPasswordHash())) {
            throw new Exceptions("error.auth.passwordSame", HttpStatus.BAD_REQUEST);
        }
        limiter.reset(key);
        applyPassword(c, req.newPassword());
        credentials.save(c);
        refresh.revokeAll(c.getId(), "PASSWORD_CHANGED");
        events.log(LoginResult.PASSWORD_CHANGED, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
        audit(actor, c, "PASSWORD_CHANGED");
        return actor.tokenType() == TokenType.SETUP ? Optional.of(proceed(c, client)) : Optional.empty();
    }

    // ------------------------------------------------------------------ cuenta propia

    @Transactional(readOnly = true)
    public MeResponse me(AuthenticatedActor actor) {
        Credential c = credential(actor);
        ContextService.Resolved r = contexts.resolve(c, actor.contextId(), actor.activeBranchId());
        boolean limited = !c.getActorType().equals(ActorType.STAFF)
                && actor.role() == RoleType.ORG_ADMIN && r.contractState() != ContractState.ACTIVE;
        return new MeResponse(contexts.userInfo(c), r.context(),
                c.getActorType() == ActorType.STAFF ? null : r.contractState().name(), limited, idleMinutes(c));
    }

    @Transactional(readOnly = true)
    public List<ContextInfo> myContexts(AuthenticatedActor actor) {
        return contexts.effectiveContexts(credential(actor));
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> mySessions(AuthenticatedActor actor) {
        return refresh.liveSessions(actor.credentialId()).stream()
                .map(t -> new SessionInfo(t.getFamilyId(), t.getIssuedAt(), t.getIp(), t.getUserAgent()))
                .toList();
    }

    public void revokeSession(AuthenticatedActor actor, UUID sessionId) {
        boolean mine = refresh.liveSessions(actor.credentialId()).stream().map(RefreshToken::getFamilyId).anyMatch(sessionId::equals);
        if (!mine) {
            throw new Exceptions("error.common.notFound", HttpStatus.NOT_FOUND);
        }
        refresh.revokeFamily(sessionId, "USER_REVOKED");
        audit(actor, credential(actor), "SESSION_REVOKED");
    }

    // ------------------------------------------------------------------ flujo compartido

    /** Tras validar las credenciales (y el MFA si aplica): pasos obligatorios → contexto → sesión. */
    private AuthResponse proceed(Credential c, ClientInfo client) {
        List<SetupStep> setup = pendingSetup(c);
        if (!setup.isEmpty()) {
            JwtService.IssuedToken t = jwt.issue(contexts.intermediate(c, TokenType.SETUP));
            return intermediate(AuthStatus.SETUP_REQUIRED, t, setup, null, contexts.userInfo(c));
        }
        if (c.getActorType() == ActorType.STAFF) {
            return startSession(c, contexts.resolve(c, c.getStaffId()), client);
        }
        List<ContextInfo> list = contexts.effectiveContexts(c);
        if (list.isEmpty()) {
            events.log(LoginResult.NO_ACCESS, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
            boolean noContract = !contexts.hasActiveContract(c.getOrganizationId());
            throw new Exceptions(noContract ? "error.auth.orgNoContract" : "error.auth.noAccess", HttpStatus.FORBIDDEN);
        }
        // Siempre se muestra la pantalla de contexto (iglesia + sede), aunque solo haya una opción
        JwtService.IssuedToken t = jwt.issue(contexts.intermediate(c, TokenType.CONTEXT));
        return intermediate(AuthStatus.CONTEXT_REQUIRED, t, null, list, contexts.userInfo(c));
    }

    private List<SetupStep> pendingSetup(Credential c) {
        List<SetupStep> steps = new java.util.ArrayList<>();
        if (c.isMustChangePassword()) {
            steps.add(SetupStep.PASSWORD_CHANGE);
        }
        if (mfaEnforced && c.getActorType() == ActorType.STAFF && !c.isMfaEnabled()) {
            steps.add(SetupStep.MFA_SETUP);
        }
        return steps;
    }

    private AuthResponse startSession(Credential c, ContextService.Resolved resolved, ClientInfo client) {
        Instant now = clock.instant();
        JwtService.IssuedToken token = jwt.issue(resolved.actor());
        RefreshTokenService.Issued refreshToken = refresh.startSession(c.getId(), resolved.actor().contextId(), resolved.actor().activeBranchId(), client);
        c.setLastLoginAt(now);
        credentials.save(c);
        if (c.getActorType() == ActorType.STAFF) {
            staffRepository.findById(c.getStaffId()).ifPresent(s -> {
                s.setLastLoginAt(now);
                staffRepository.save(s);
            });
        }
        events.log(LoginResult.SUCCESS, c.getId(), c.getActorType(), c.getOrganizationId(), c.getUsername(), client, null);
        return authenticated(c, resolved, token, refreshToken.rawToken());
    }

    private AuthResponse authenticated(Credential c, ContextService.Resolved r, JwtService.IssuedToken token, String rawRefresh) {
        return new AuthResponse(AuthStatus.AUTHENTICATED, token.token(), token.expiresAt(), rawRefresh, null, null,
                contexts.userInfo(c), r.context(),
                c.getActorType() == ActorType.STAFF ? null : r.contractState().name(), idleMinutes(c), null);
    }

    private static AuthResponse intermediate(AuthStatus status, JwtService.IssuedToken t, List<SetupStep> setup,
                                             List<ContextInfo> list, pe.dcs.app.features.auth.dto.UserInfo user) {
        return new AuthResponse(status, t.token(), t.expiresAt(), null, setup, list, user, null, null, null, null);
    }

    private int idleMinutes(Credential c) {
        return c.getActorType() == ActorType.STAFF ? idleMinutesPlatform : idleMinutesOrg;
    }

    private void applyPassword(Credential c, String newPassword) {
        c.setPasswordHash(encoder.encode(newPassword));
        c.setMustChangePassword(false);
        c.setPasswordChangedAt(clock.instant());
    }

    private Credential credential(AuthenticatedActor actor) {
        return credentials.findById(actor.credentialId())
                .orElseThrow(() -> new Exceptions("error.auth.sessionExpired", HttpStatus.UNAUTHORIZED));
    }

    private static String rateKey(ClientInfo client, String scope, String username) {
        return client.ip() + "|" + scope + "|" + username.trim().toLowerCase();
    }

    private void audit(AuthenticatedActor actor, Credential c, String action) {
        audit.record(new AuditService.Command(MODULE, action, "Credential", c.getId(),
                actor.organizationId(), null, Map.of()));
    }

    /** Acciones sin sesión (reset, invitación): el actor queda como SYSTEM y se anota la credencial afectada. */
    private void auditAnonymous(Credential c, String action) {
        audit.record(new AuditService.Command(MODULE, action, "Credential", c.getId(),
                c.getOrganizationId(), null, Map.of("actorType", c.getActorType().name())));
    }
}
