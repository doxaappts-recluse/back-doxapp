package pe.dcs.app.features.access.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Delegación a un ORG_USER: módulo × acciones (solo módulos contratados y delegables, [V12]/[V13]). */
@Getter
@Setter
@Entity
@Table(name = "user_access_permission")
public class UserAccessPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "access_id", nullable = false)
    private UUID accessId;

    @Column(name = "module_code", nullable = false, length = 40)
    private String moduleCode;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "actions", nullable = false, columnDefinition = "text[]")
    private String[] actions;

    public List<String> actionList() {
        return actions == null ? List.of() : Arrays.asList(actions);
    }
}
