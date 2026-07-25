package pe.dcs.app.util.enums.resolveSort;

import java.util.Arrays;

public enum OrgAdminBranchSort {

    NAME("name"),
    LASTNAME("lastname"),
    USERNAME("credential.username"),
    ORGANIZATION("accesses.organization.name"),
    BRANCH("accesses.branch.name");

    private final String path;


    OrgAdminBranchSort(String path){
        this.path = path;
    }

    public static String resolvePath(
            String field
    ){

        return Arrays.stream(values())
                .filter(
                        x -> x.name()
                                .equalsIgnoreCase(field)
                )
                .findFirst()
                .map(
                        x -> x.path
                )
                .orElse("name");

    }

}