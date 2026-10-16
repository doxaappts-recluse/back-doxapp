package pe.dcs.app.features.organization.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M02 · Identidad visual de una organización (1:1). Nulo = usa el valor por defecto de DoxApp / el nombre de la
 * organización. Los archivos se guardan con {@code FileStorageService}; aquí solo su clave.
 */
@Getter
@Setter
@Entity
@Table(name = "organization_branding")
public class OrganizationBranding extends Auditable {

    @Id
    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "display_name", length = 60)
    private String displayName;

    @Column(name = "logo_light_key", length = 255)
    private String logoLightKey;

    @Column(name = "logo_dark_key", length = 255)
    private String logoDarkKey;

    @Column(name = "favicon_key", length = 255)
    private String faviconKey;

    @Column(name = "login_background_key", length = 255)
    private String loginBackgroundKey;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "welcome_text_es", length = 300)
    private String welcomeTextEs;

    @Column(name = "welcome_text_en", length = 300)
    private String welcomeTextEn;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "socials", columnDefinition = "jsonb")
    private Map<String, String> socials = new LinkedHashMap<>();

    /** Sube con cada cambio: versiona las URLs de los archivos para que el navegador no use una copia vieja. */
    @Column(name = "revision", nullable = false)
    private int revision = 1;
}
