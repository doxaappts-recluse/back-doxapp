package pe.dcs.app.util.enums.resolveSort;

import java.util.Arrays;

/**
 * Rutas relativas a UserAccess (root de la Specification desde
 * el refactor a accesos): la persona y su credencial se alcanzan
 * vía las relaciones "person" / "person.credential".
 */
public enum AccessUserSort {

    NAME("person.name"),
    LASTNAME("person.lastname"),
    USERNAME("person.credential.username");

    private final String path;

    AccessUserSort(String path) {
        this.path = path;
    }

    public static String resolvePath(
            String field
    ) {

        return Arrays.stream(values())
                .filter(
                        x -> x.name()
                                .equalsIgnoreCase(field)
                )
                .findFirst()
                .map(
                        x -> x.path
                )
                .orElse("person.name");

    }

}
