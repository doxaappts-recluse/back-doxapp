package pe.dcs.app.features.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * M23 · Ítem de catálogo. {@code organizationId} nulo = BASE (lo publica la plataforma y lo heredan todas las
 * organizaciones); con organización = ORG (propio de esa organización, nunca altera a los BASE).
 */
@Getter
@Setter
@Entity
@Table(name = "catalog_item")
public class CatalogItem extends Auditable {

    public static final String BASE = "BASE";
    public static final String ORG = "ORG";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "type", nullable = false, length = 40)
    private String type;

    @Column(name = "code", nullable = false, length = 60)
    private String code;

    @Column(name = "name_es", nullable = false, length = 120)
    private String nameEs;

    @Column(name = "name_en", nullable = false, length = 120)
    private String nameEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "source", nullable = false, length = 10)
    private String source;

    @Column(name = "parent_id")
    private UUID parentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> meta = new HashMap<>();

    public boolean isBase() {
        return BASE.equals(source);
    }
}
