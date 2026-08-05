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

@Entity
@Table(
        name = "modules",
        indexes = {

                @Index(
                        name = "idx_module_code",
                        columnList = "code"
                ),

                @Index(
                        name = "idx_module_status",
                        columnList = "status"
                ),

                @Index(
                        name = "idx_module_parent",
                        columnList = "parent_id"
                )

        }
)
@Getter
@Setter
public class Module extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    // =========================
    // INFORMATION
    // =========================

    /*
     * Igual patrón que Ministry/FinancialFund: nameEs/nameEn son
     * solo para visualización, getLocalizedName() resuelve cuál
     * mostrar según el locale de la request. El sidebar (ver
     * SidebarMapper/ModuleResponse) manda AMBOS al frontend para
     * que el cambio de idioma se refleje ahí sin llamar de nuevo
     * al backend.
     */
    @Column(name = "name_es", nullable = false)
    private String nameEs;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(
            nullable = false,
            unique = true
    )
    private String code;

    private String icon;

    private String route;

    private Integer orderNum;

    // =========================
    // STATUS
    // =========================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusType status;

    // =========================
    // VISIBILIDAD POR ROL
    // =========================

    /**
     * Controla, por rol, si este módulo puede llegar a
     * aparecer alguna vez para ese rol (en el sidebar y en
     * el catálogo de módulos asignables a un contrato).
     *
     * Es un filtro ESTRUCTURAL, independiente del contrato:
     * un módulo puede ser visibleOrgAdmin=true y aun así no
     * mostrarse si el contrato de esa organización no lo
     * incluye.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean visibleSystem = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean visibleOrgAdmin = true;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean visibleBranchAdmin = true;

    /**
     * Controla si este módulo puede llegar a asignarse a un
     * ORG_USER (usuario normal, sin rol de admin) mediante
     * Usuarios de Acceso. Igual que los otros: es un filtro
     * estructural, independiente de si el contrato lo habilita.
     */
    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean visibleUser = true;

    // =========================
    // TREE STRUCTURE
    // =========================

    /**
     * Módulo padre.
     *
     * Ejemplo:
     *
     * Usuarios
     *    |
     *    +-- Lista
     *    +-- Crear
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Module parent;

    /**
     * Hijos del módulo.
     */
    @OneToMany(
            mappedBy = "parent",
            fetch = FetchType.LAZY
    )
    private List<Module> children =
            new ArrayList<>();

    // =========================
    // HELPERS
    // =========================

    public boolean isActive(){
        return status == StatusType.ACTIVE;
    }

    public boolean isRoot(){
        return parent == null;
    }

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