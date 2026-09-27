package pe.dcs.app.features.support.domain;

/** OPEN (recién llegado o reabierto) · WAITING_ORG (esperando a la organización) · WAITING_PLATFORM (esperando a plataforma) · RESOLVED · CLOSED (final). */
public enum SupportStatus {
    OPEN, WAITING_ORG, WAITING_PLATFORM, RESOLVED, CLOSED;

    public boolean isOpenish() {
        return this == OPEN || this == WAITING_ORG || this == WAITING_PLATFORM;
    }
}
