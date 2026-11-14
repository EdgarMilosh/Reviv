package lt.reviv.supplements.domain;

import jakarta.persistence.*;

/** Platintojas: parduotuvė, kurioje galima įsigyti papildą. */
@Entity
@Table(name = "supplement_distributor")
public class Distributor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplement_id", nullable = false)
    private Supplement supplement;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    protected Distributor() {
    }

    public Distributor(Supplement supplement, String name, String url) {
        this.supplement = supplement;
        this.name = name;
        this.url = url;
    }

    public Long getId() { return id; }
    public Supplement getSupplement() { return supplement; }
    public String getName() { return name; }
    public String getUrl() { return url; }
}