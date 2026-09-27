package pe.dcs.app.features.access.domain;

/** Estados de acceso (spec 00 §4): INVITED hasta aceptar invitación; ACTIVE/INACTIVE; LOCKED por seguridad. */
public enum AccessStatus {
    INVITED, ACTIVE, INACTIVE, LOCKED
}
