package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.context.i18n.LocaleContextHolder;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Catálogo administrado por SYSTEM. {@code code} es la identidad
 * estable (usada por lógica interna, p.ej. find-or-create de
 * ministerios de referencia en SmallGroupServiceImpl/
 * BibleAcademyServiceImpl) — no se traduce y no debería cambiar
 * tras la creación. {@code nameEs}/{@code nameEn} son solo para
 * visualización; {@link #getLocalizedName()} resuelve cuál mostrar
 * según el locale de la request (mismo criterio que Exceptions/
 * ApiResponse, ver MessageSourceHolder).
 */
@Entity
@Table(
        name = "ministries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ministry_code",
                        columnNames = "code"
                )
        }
)
@Getter
@Setter
public class Ministry extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status = StatusType.ACTIVE;

    @OneToMany(
            mappedBy = "ministry",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<MinistryRole> roles = new ArrayList<>();

    @Column(nullable = false)
    private Boolean requiresActiveMembership = true;

    /**
     * Nombre a mostrar según el idioma de la request actual. En
     * inglés cae a nameEs si nameEn no está cargado (y viceversa)
     * para no mostrar vacío ante datos legado incompletos.
     */
    public String getLocalizedName() {

        boolean english =
                LocaleContextHolder.getLocale() != null
                        && "en".equalsIgnoreCase(LocaleContextHolder.getLocale().getLanguage());

        if (english) {
            return nameEn != null ? nameEn : nameEs;
        }

        return nameEs != null ? nameEs : nameEn;
    }

}