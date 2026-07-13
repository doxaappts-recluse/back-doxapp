package pe.dcs.app.features.branch.mapper;

import pe.dcs.app.entity.Branch;
import pe.dcs.app.features.branch.response.BranchResponse;
import pe.dcs.app.util.enums.StatusType;

public class BranchMapper {

    private BranchMapper() {
    }

    public static BranchResponse toResponse(Branch branch){

        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .main(branch.getMain())
                .openingDate(branch.getOpeningDate())
                .status(branch.getStatus() == StatusType.ACTIVE)
                .build();
    }
}