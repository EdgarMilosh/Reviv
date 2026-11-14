package lt.reviv.supplements.domain;

import jakarta.persistence.*;

/**
 * Dviejų papildų sąveika. Saugomas vienas įrašas porai (mažesnis ID visada supplementAId),
 * todėl ji automatiškai taikoma abiem papildams.
 */
@Entity
@Table(name = "supplement_interaction")
public class SupplementInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplement_a_id", nullable = false)
    private Long supplementAId;

    @Column(name = "supplement_b_id", nullable = false)
    private Long supplementBId;

    @Column(nullable = false, length = 100)
    private String type;

    @Column(nullable = false, length = 500)
    private String recommendation;

    protected SupplementInteraction() {
    }

    public SupplementInteraction(Long firstSupplementId, Long secondSupplementId, String type, String recommendation) {
        this.supplementAId = Math.min(firstSupplementId, secondSupplementId);
        this.supplementBId = Math.max(firstSupplementId, secondSupplementId);
        this.type = type;
        this.recommendation = recommendation;
    }

    /** Grąžina kitą poros papildą nei nurodytas. */
    public Long otherSupplementId(Long supplementId) {
        return supplementAId.equals(supplementId) ? supplementBId : supplementAId;
    }

    public Long getId() { return id; }
    public Long getSupplementAId() { return supplementAId; }
    public Long getSupplementBId() { return supplementBId; }
    public String getType() { return type; }
    public String getRecommendation() { return recommendation; }
}