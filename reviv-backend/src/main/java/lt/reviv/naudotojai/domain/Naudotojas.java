package lt.reviv.naudotojai.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "naudotojas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Naudotojas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId;

    @Column(name = "el_pastas", nullable = false)
    private String elPastas;

    private String vardas;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NaudotojoRole role;

    @Column(name = "sutikimo_data", nullable = false)
    private OffsetDateTime sutikimoData;

    @Column(nullable = false)
    private OffsetDateTime sukurta;

    public Naudotojas(String googleId, String elPastas, String vardas, NaudotojoRole role) {
        this.googleId = googleId;
        this.elPastas = elPastas;
        this.vardas = vardas;
        this.role = role;
        this.sutikimoData = OffsetDateTime.now();
        this.sukurta = OffsetDateTime.now();
    }

    /** Google paskyroje pasikeitus el. paštui ar vardui, atnaujiname juos. */
    public void atnaujintiDuomenis(String elPastas, String vardas) {
        this.elPastas = elPastas;
        this.vardas = vardas;
    }
}