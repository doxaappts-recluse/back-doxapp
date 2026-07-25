package pe.dcs.app.util.enums.resolveSort;

import java.util.Arrays;

public enum PersonSort {

    NAME("name", "name"),
    LASTNAME("lastname", "lastname"),
    DNI("dni", "dni"),
    USERNAME("username", "credential.username"),
    ROLE_NAME("roleName", "accesses.role.name"),
    ROLE_CODE("roleCode", "accesses.role.value"),
    STATUS("status", "status");

    private final String key;
    private final String path;

    PersonSort(String key, String path) {
        this.key = key;
        this.path = path;
    }

    public static String resolvePath(String key) {
        return Arrays.stream(values())
                .filter(e -> e.key.equals(key))
                .map(e -> e.path)
                .findFirst()
                .orElse(null);
    }

    public static PersonSort fromKey(String key) {
        return Arrays.stream(values())
                .filter(e -> e.key.equals(key))
                .findFirst()
                .orElse(null);
    }
}