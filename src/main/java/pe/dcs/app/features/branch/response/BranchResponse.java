package pe.dcs.app.features.branch.response;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BranchResponse {
    private UUID id;
    private String name;
    private String code;
    private String address;
    private String phone;
    private String email;
    private Boolean main;
    private Boolean status;
    private LocalDate openingDate;
}