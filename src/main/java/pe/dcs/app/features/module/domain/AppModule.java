package pe.dcs.app.features.module.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Arrays;
import java.util.List;

/** M03 · Entrada del catálogo de módulos. El código es inmutable y es la clave de permisos (p. ej. PERSON, M06.PERSON). */
@Getter
@Setter
@Entity
@Table(name = "module")
public class AppModule {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name_es", nullable = false, length = 100)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "levels", nullable = false, columnDefinition = "text[]")
    private String[] levels;

    @Column(name = "parent_code", length = 40)
    private String parentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private ModuleKind kind;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "actions", nullable = false, columnDefinition = "text[]")
    private String[] actions;

    @Column(name = "delegable", nullable = false)
    private boolean delegable = true;

    @Column(name = "route", length = 120)
    private String route;

    @Column(name = "icon", length = 60)
    private String icon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PUBLISHED";

    public List<String> levelList() {
        return levels == null ? List.of() : Arrays.asList(levels);
    }

    public List<String> actionList() {
        return actions == null ? List.of() : Arrays.asList(actions);
    }

    public boolean isPublished() {
        return "PUBLISHED".equals(status);
    }
}
