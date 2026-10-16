package pe.dcs.app.features.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pe.dcs.app.features.auth.domain.TokenPurpose;
import pe.dcs.app.features.config.service.SettingsService;
import pe.dcs.app.features.organization.domain.OrganizationRepository;
import pe.dcs.app.shared.mail.MailMessage;
import pe.dcs.app.shared.mail.MailPort;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Invitación (alta) y recuperación de contraseña por correo. Genera el token de un solo uso y arma el enlace
 * al front: {@code /platform/...} para personal de plataforma, {@code /o/{slug}/...} para organizaciones.
 */
@Service
public class InvitationService {

    private final OneTimeTokenService tokens;
    private final MailPort mail;
    private final String frontendUrl;

    private final OrganizationRepository organizations;
    private final SettingsService settings;

    public InvitationService(OneTimeTokenService tokens, MailPort mail, OrganizationRepository organizations, SettingsService settings,
                             @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.tokens = tokens;
        this.mail = mail;
        this.organizations = organizations;
        this.settings = settings;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Invita (o reenvía la invitación): invalida la anterior y envía un enlace nuevo válido 72 h. */
    public void sendInvite(UUID credentialId, String toEmail, String displayName, String organizationSlug, String locale) {
        // M23: la vigencia de la invitación es un ajuste (sede → organización → plataforma → 72 h)
        Duration ttl = organizationSlug == null ? Duration.ofHours(settings.getInt(null, null, "ACCESS", "invitationValidityHours"))
                : organizations.findBySlug(organizationSlug)
                .map(o -> Duration.ofHours(settings.getInt(o.getId(), null, "ACCESS", "invitationValidityHours")))
                .orElse(Duration.ofHours(72));
        String token = tokens.issue(credentialId, TokenPurpose.INVITE, ttl);
        String path = organizationSlug == null ? "/platform/accept-invite" : "/o/" + organizationSlug + "/accept-invite";
        mail.send(new MailMessage(toEmail, "auth.invite", locale,
                Map.of("name", displayName, "link", frontendUrl + path + "?token=" + token)));
    }

    /** Enlace de recuperación válido 60 min. */
    public void sendPasswordReset(UUID credentialId, String toEmail, String displayName, String organizationSlug, String locale) {
        String token = tokens.issue(credentialId, TokenPurpose.RESET);
        String path = organizationSlug == null ? "/platform/reset-password" : "/o/" + organizationSlug + "/reset-password";
        mail.send(new MailMessage(toEmail, "auth.passwordReset", locale,
                Map.of("name", displayName, "link", frontendUrl + path + "?token=" + token)));
    }
}
