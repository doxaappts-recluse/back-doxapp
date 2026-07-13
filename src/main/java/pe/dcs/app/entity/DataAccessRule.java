package pe.dcs.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import pe.dcs.app.util.auditable.Auditable;
import pe.dcs.app.util.enums.rules.DataScope;

import java.util.UUID;

@Entity
@Table(
        name = "data_access_rules",
        indexes = {
                @Index(name = "idx_data_rule_module", columnList = "module_id"),
                @Index(name = "idx_data_rule_scope", columnList = "scope"),
                @Index(name = "idx_data_rule_enabled", columnList = "enabled")
        }
)
@Getter
@Setter
public class DataAccessRule extends Auditable {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private Module module;

    /** Tipo de información visible. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataScope scope;

    /** Requiere aprobación de la sede propietaria. */
    @Column(nullable = false)
    private Boolean requiresApproval = true;

    @Column(nullable = false)
    private Boolean enabled = true;

}