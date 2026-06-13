package pe.dcs.app.features.module.response;

import java.util.List;
import java.util.UUID;

public interface SidebarModuleProjection {

    UUID getId();
    String getName();
    String getCode();
    String getIcon();
    String getRoute();
    Integer getOrderNum();
    UUID getParentId();
}