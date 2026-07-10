package pe.dcs.app.features.branch.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BranchUpdateRequest {
    private String name;
    private String code;
    private String address;
    private String phone;
    private String email;
}