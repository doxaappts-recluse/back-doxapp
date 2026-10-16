package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.dcs.app.features.auth.domain.OneTimeToken;
import pe.dcs.app.features.auth.domain.TokenPurpose;
import pe.dcs.app.features.auth.repo.OneTimeTokenRepository;
import pe.dcs.app.util.Exceptions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Tokens de un solo uso: invitación (72 h) y recuperación de contraseña (60 min).
 * Emitir uno invalida los pendientes del mismo propósito. El valor en claro solo se devuelve al emitirlo.
 */
@Service
public class OneTimeTokenService {

    private final OneTimeTokenRepository repository;
    private final Clock clock;
    private final Duration inviteTtl;
    private final Duration resetTtl;

    public OneTimeTokenService(OneTimeTokenRepository repository, Clock clock,
                               @Value("${app.security.invite-ttl-hours:72}") long inviteHours,
                               @Value("${app.security.reset-ttl-minutes:60}") long resetMinutes) {
        this.repository = repository;
        this.clock = clock;
        this.inviteTtl = Duration.ofHours(inviteHours);
        this.resetTtl = Duration.ofMinutes(resetMinutes);
    }

    @Transactional
    public String issue(UUID credentialId, TokenPurpose purpose) {
        return issue(credentialId, purpose, purpose == TokenPurpose.INVITE ? inviteTtl : resetTtl);
    }

    /** Igual que {@link #issue(UUID, TokenPurpose)} con vigencia propia (M23: ajuste ACCESS.invitationValidityHours). */
    @Transactional
    public String issue(UUID credentialId, TokenPurpose purpose, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidatePending(credentialId, purpose, now);
        String raw = TokenCodec.newOpaqueToken();
        OneTimeToken t = new OneTimeToken();
        t.setPurpose(purpose);
        t.setCredentialId(credentialId);
        t.setTokenHash(TokenCodec.sha256Hex(raw));
        t.setCreatedAt(now);
        t.setExpiresAt(now.plus(ttl));
        repository.save(t);
        return raw;
    }

    /**
     * Comprueba que el token sea utilizable SIN consumirlo (para validar el resto de la petición antes de gastarlo).
     * Sin @Transactional propio: un @Transactional interno marcaría la transacción del llamador como rollback-only al lanzar.
     */
    public UUID peek(String rawToken, TokenPurpose purpose) {
        OneTimeToken t = rawToken == null ? null : repository.findByTokenHash(TokenCodec.sha256Hex(rawToken)).orElse(null);
        if (t == null || t.getPurpose() != purpose || !t.isUsable(clock.instant())) {
            throw new Exceptions("error.auth.tokenInvalid", HttpStatus.BAD_REQUEST);
        }
        return t.getCredentialId();
    }

    /**
     * Consume el token (un solo uso). 400 {@code error.auth.tokenInvalid} si no existe, es de otro propósito,
     * venció o ya se usó: el mensaje no distingue el motivo. Devuelve el id de la credencial.
     * Sin @Transactional propio: se une a la transacción del llamador (que decide qué hacer con la excepción).
     */
    public UUID consume(String rawToken, TokenPurpose purpose) {
        Instant now = clock.instant();
        OneTimeToken t = rawToken == null ? null : repository.findByTokenHash(TokenCodec.sha256Hex(rawToken)).orElse(null);
        if (t == null || t.getPurpose() != purpose || !t.isUsable(now)) {
            throw new Exceptions("error.auth.tokenInvalid", HttpStatus.BAD_REQUEST);
        }
        t.setUsedAt(now);
        repository.save(t);
        return t.getCredentialId();
    }
}
