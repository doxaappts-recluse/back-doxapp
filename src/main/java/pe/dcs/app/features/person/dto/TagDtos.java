package pe.dcs.app.features.person.dto;

import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/** M06 · Etiquetas de personas (/admin/tags). */
public final class TagDtos {

    private TagDtos() {
    }

    public record Request(@Size(max = 40, message = "error.common.tooLong") String name, @Size(max = 7, message = "error.common.tooLong") String color, Long version) {
    }

    public record Response(UUID id, String name, String color, long usage, Long version, Instant createdAt) {
    }
}
