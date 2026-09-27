package pe.dcs.app.features.config.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.Auditable;

import java.util.HashMap;
import java.util.Map;

/** Valor por defecto de plataforma de un ajuste ({@code NAMESPACE.key}). Versionado (@Version) y auditado. */
@Getter
@Setter
@Entity
@Table(name = "platform_setting")
public class PlatformSetting extends Auditable {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> value = new HashMap<>();

    @Column(name = "description", length = 255)
    private String description;

    public Object raw() {
        return value == null ? null : value.get("v");
    }

    public void set(Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put("v", v);
        this.value = m;
    }
}
