package pe.dcs.app.features.integration.service;

/** [V11] Verificación SPF/DKIM del dominio del remitente. */
public interface DomainVerifier {
    boolean verified(String domain, String dkimSelector);
}
