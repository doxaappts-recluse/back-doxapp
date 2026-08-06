package pe.dcs.app.util;

import pe.dcs.app.entity.Person;
import pe.dcs.app.entity.UserAccess;
import pe.dcs.app.util.enums.RoleType;
import pe.dcs.app.util.enums.StatusType;

import java.util.List;
import java.util.UUID;

public class UserAccessHelper {

    private UserAccessHelper(){}

    public static UserAccess getAccessByRole(Person person, RoleType role){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getRole() != null
                                        &&
                                        a.getRole()
                                                .getValue()
                                                == role
                )
                .findFirst()
                .orElse(null);
    }

    public static UserAccess getActiveAccess(Person person){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .findFirst()
                .orElse(null);
    }

    public static UserAccess getActiveAccess(Person person, UUID organizationId){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .filter(
                        a ->
                                a.getOrganization() != null
                                        &&
                                        a.getOrganization()
                                                .getId()
                                                .equals(
                                                        organizationId
                                                )
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * Todos los accesos ACTIVOS de la persona. Una persona puede
     * tener varios (uno por sede/rol), salvo que uno de ellos sea
     * ORG_ADMIN (acceso global, no debería convivir con otros).
     */
    public static List<UserAccess> getActiveAccesses(Person person){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .toList();
    }

    /**
     * El acceso ACTIVO de la persona con el rol puntual indicado.
     * A diferencia de {@link #getActiveAccess(Person)}, no asume
     * que la persona tiene un único acceso: busca específicamente
     * el que tiene ese rol.
     */
    public static UserAccess getActiveAccessByRole(Person person, RoleType role){

        return person.getAccesses()
                .stream()
                .filter(
                        a ->
                                a.getActive()
                                        == StatusType.ACTIVE
                )
                .filter(
                        a ->
                                a.getRole() != null
                                        &&
                                        a.getRole().getValue() == role
                )
                .findFirst()
                .orElse(null);
    }

    /**
     * true si alguno de los accesos ACTIVOS de la persona es
     * ORG_ADMIN (acceso global a la organización).
     */
    public static boolean hasActiveOrganizationAdminAccess(Person person){

        return getActiveAccesses(person)
                .stream()
                .anyMatch(UserAccess::isOrganizationAdmin);
    }

    /**
     * Acceso "de referencia" para mostrar en pantallas de listado
     * (organización/sede/rol), aunque la persona no tenga ningún
     * acceso ACTIVO (p.ej. le deshabilitaron todos). Sin esto, una
     * persona totalmente deshabilitada mostraría esas columnas
     * vacías en vez de la última asignación que tuvo.
     *
     * Prioridad: acceso activo > acceso ORG_ADMIN (activo o no) >
     * primer acceso registrado.
     */
    public static UserAccess getDisplayAccess(Person person){

        UserAccess active = getActiveAccess(person);

        if(active != null){
            return active;
        }

        return person.getAccesses()
                .stream()
                .filter(UserAccess::isOrganizationAdmin)
                .findFirst()
                .orElseGet(() ->
                        person.getAccesses()
                                .stream()
                                .findFirst()
                                .orElse(null)
                );
    }

}