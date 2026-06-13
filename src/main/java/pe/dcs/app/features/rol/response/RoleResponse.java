package pe.dcs.app.features.rol.response;

import pe.dcs.app.entity.Role;
import java.util.UUID;
import pe.dcs.app.util.enums.StatusType;

public class RoleResponse {

    private UUID id;
    private String name;
    private String value;
    private StatusType status;

    public RoleResponse(Role role) {

        this.id = role.getId();
        this.name = role.getName();
        this.value = role.getValue();
        this.status = role.getStatus();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public StatusType getStatus() {
        return status;
    }

    public void setStatus(StatusType status) {
        this.status = status;
    }
}