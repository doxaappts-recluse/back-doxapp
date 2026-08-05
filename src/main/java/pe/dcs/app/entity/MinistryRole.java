package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.context.i18n.LocaleContextHolder;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.StatusType;

import java.util.UUID;

/**
 * Mismo patrón code/nameEs/nameEn que {@link Ministry} — ver ahí
 * el porqué. code es único por ministerio (no global).
 */
@Entity
@Table(
        name = "ministry_roles",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ministry_role_code",
                        columnNames = {
                                "ministry_id",
                                "code"
                        }
                )
        }
)
@Getter
@Setter
public class MinistryRole extends Auditable {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministry_id", nullable = false)
    private Ministry ministry;

    @Column(nullable = false)
    private Boolean requiresActiveMembership = true;

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