package pe.dcs.app.features.branch.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchCreateRequest {
    private String name;
    private String code;
    private String address;
    private String phone;
    private String email;
}