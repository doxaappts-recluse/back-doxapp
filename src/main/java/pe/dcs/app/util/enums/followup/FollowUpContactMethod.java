package pe.dcs.app.util.enums.followup;

/**
 * Medio por el que se realizó un contacto de seguimiento pastoral
 * (ver {@link pe.dcs.app.entity.FollowUpContact}).
 */
public enum FollowUpContactMethod {

    CALL,       // Llamada telefónica
    WHATSAPP,   // Mensaje de WhatsApp
    VISIT,      // Visita presencial (domicilio u otro lugar)
    IN_PERSON,  // Conversación presencial (p.ej. en el templo)
    EMAIL,      // Correo electrónico
    OTHER       // Otro medio

}
