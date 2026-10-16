package pe.dcs.app.features.person.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import pe.dcs.app.shared.domain.TenantScoped;
import pe.dcs.app.shared.vo.Address;
import pe.dcs.app.shared.vo.DocumentType;

import java.time.LocalDate;
import java.util.UUID;

/** M06 · Persona. En F0: identidad mínima que respalda credenciales y accesos; M06 amplía la ficha. */
@Getter
@Setter
@Entity
@Table(name = "person")
public class Person extends TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", length = 10)
    private DocumentType docType;

    @Column(name = "doc_number", length = 12)
    private String docNumber;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "email", length = 160)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "sex", length = 10)
    private String sex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "marital_status", length = 15)
    private String maritalStatus;

    @Column(name = "whatsapp", length = 20)
    private String whatsapp;

    @Embedded
    private Address address = new Address();

    /** Clave del archivo de la foto en el almacenamiento (org/{org}/persons/{id}/photo.jpg); nula si no tiene. */
    @Column(name = "photo_key", length = 300)
    private String photoKey;

    @Column(name = "photo_updated_at")
    private java.time.Instant photoUpdatedAt;

    @Column(name = "occupation", length = 120)
    private String occupation;

    @Column(name = "primary_branch_id")
    private UUID primaryBranchId;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @Column(name = "merged_into")
    private UUID mergedInto;

    @Column(name = "deceased_at")
    private LocalDate deceasedAt;

    @Column(name = "status_reason", length = 300)
    private String statusReason;

    /** Cifrado en reposo (SecretCipher). Solo se lee con la acción H. */
    @Column(name = "private_notes")
    private String privateNotes;

    @Column(name = "allergies")
    private String allergies;

    /** Momento de la anonimización (irreversible); nulo si la persona conserva su identidad. */
    @Column(name = "anonymized_at")
    private java.time.Instant anonymizedAt;

    public String fullName() {
        return (firstName + " " + lastName).trim();
    }
}
