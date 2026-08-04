package pe.dcs.app.util.enums;

/**
 * Tipo de documento al que aplica una plantilla subida en el
 * módulo "Plantillas de Documentos" — extensible: agregar acá
 * nuevos tipos de certificado/documento a futuro no requiere tocar
 * el resto del feature (entity/mapper/service/controller son
 * genéricos sobre este enum).
 */
public enum DocumentTemplateType {
    BAPTISM_CERTIFICATE,
    DONATION_CERTIFICATE,
    MEMBERSHIP_CERTIFICATE,
    MARRIAGE_CERTIFICATE,
    BIBLE_ACADEMY_CERTIFICATE,
    OTHER
}
