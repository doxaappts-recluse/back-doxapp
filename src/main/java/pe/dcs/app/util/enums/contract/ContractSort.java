package pe.dcs.app.util.enums.contract;

import java.util.Arrays;

public enum ContractSort {

    ORGANIZATION_NAME("organizationName", "organization.name"),
    PLAN_NAME("planName", "planName"),
    PRICE("price", "price"),
    CURRENCY("currency", "currency"),
    START_DATE("startDate", "startDate"),
    END_DATE("endDate", "endDate"),
    NUMBER_USERS("numberUsers", "numberUsers"),
    STATUS("status", "status");

    private final String key;
    private final String path;

    ContractSort(String key, String path) {
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

    public static ContractSort fromKey(String key) {
        return Arrays.stream(values())
                .filter(e -> e.key.equals(key))
                .findFirst()
                .orElse(null);
    }
}