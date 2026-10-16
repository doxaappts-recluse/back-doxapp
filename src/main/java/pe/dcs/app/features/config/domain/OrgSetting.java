package pe.dcs.app.features.config.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Valor de un ajuste para una organización (branchId nulo) o una sede (override). El valor viaja como {"v": …}. */
@Getter
@Setter
@Entity
@Table(name = "org_setting")
public class OrgSetting extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "namespace", nullable = false, length = 40)
    private String namespace;

    @Column(name = "setting_key", nullable = false, length = 60)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> value = new HashMap<>();

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion = 1;

    public Object raw() {
        return value == null ? null : value.get("v");
    }

    public void set(Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        this.value = m;
    }
}
