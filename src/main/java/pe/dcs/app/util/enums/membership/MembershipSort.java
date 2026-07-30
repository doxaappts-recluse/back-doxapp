package pe.dcs.app.util.enums.membership;

import java.util.Arrays;

public enum MembershipSort {

    START_DATE("startDate", "startDate"),
    END_DATE("endDate", "endDate"),
    STATUS("status", "status"),
    CURRENT("current", "current");

    private final String key;
    private final String path;

    MembershipSort(String key, String path) {
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
}
