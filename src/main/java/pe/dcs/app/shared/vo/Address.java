package pe.dcs.app.shared.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Núcleo 01 §1 · Address. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class Address {

    @Column(name = "address_line", length = 200)
    private String line;

    @Column(name = "address_district", length = 80)
    private String district;

    @Column(name = "address_city", length = 80)
    private String city;

    @Column(name = "address_region", length = 80)
    private String region;

    @Column(name = "address_country", length = 2)
    private String country;

    @Column(name = "address_reference", length = 200)
    private String reference;
}
