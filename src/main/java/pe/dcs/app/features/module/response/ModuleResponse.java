package pe.dcs.app.features.module.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Data
public class ModuleResponse {

    private UUID id;

    private String name;
    private String code;
    private String icon;
    private String route;

    private Integer orderNum;

    private Boolean root;
    private Boolean status;

    // parent
    private UUID parentId;
    private String parentName;

    // sidebar
    private List<String> permissions;
    private List<ModuleResponse> children;

    private String source;

    public boolean hasContent() {
        return (permissions != null && !permissions.isEmpty())
                || (children != null && !children.isEmpty());
    }
}