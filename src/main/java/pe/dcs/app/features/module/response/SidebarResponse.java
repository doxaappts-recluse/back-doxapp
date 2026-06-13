package pe.dcs.app.features.module.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SidebarResponse {
    private String id;
    private String label;
    private String icon;
    private String route;
    private List<String> permissions;
    private List<SidebarResponse> children;
}