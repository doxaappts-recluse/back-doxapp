package pe.dcs.app.features.access.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.dcs.app.shared.domain.TenantScoped;
import pe.dcs.app.util.enums.StatusType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * M05 · Perfil de permisos reutilizable de una organización: lista de módulo × acciones. Al aplicarlo a un acceso se
 * COPIA (no queda enlazado): cambiar el perfil después no cambia lo ya otorgado.
 */
@Getter
@Setter
@Entity
@Table(name = "permission_profile")
public class PermissionProfile extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusType status = StatusType.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "permission_profile_item", joinColumns = @JoinColumn(name = "profile_id"))
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    @Embeddable
    public static class Item {

        @Column(name = "module_code", nullable = false, length = 40)
        private String moduleCode;

        @JdbcTypeCode(SqlTypes.ARRAY)
        @Column(name = "actions", nullable = false, columnDefinition = "text[]")
        private String[] actions;

        public Item() {
        }

        public Item(String moduleCode, List<String> actions) {
            this.moduleCode = moduleCode;
            this.actions = actions.toArray(new String[0]);
        }

        public List<String> actionList() {
            return actions == null ? List.of() : Arrays.asList(actions);
        }
    }
}
