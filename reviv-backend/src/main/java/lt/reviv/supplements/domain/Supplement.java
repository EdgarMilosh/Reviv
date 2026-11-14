package lt.reviv.supplements.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "supplement")
public class Supplement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "active_substance", nullable = false, length = 120)
    private String activeSubstance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplementForm form;

    @Column(name = "dose_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal doseAmount;

    @Column(name = "dose_unit", nullable = false, length = 20)
    private String doseUnit;

    @Column(name = "max_daily_dose", nullable = false, precision = 12, scale = 4)
    private BigDecimal maxDailyDose;

    @Column(name = "missed_dose_rule", length = 500)
    private String missedDoseRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupplementStatus status = SupplementStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "supplement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Distributor> distributors = new ArrayList<>();

    protected Supplement() {
    }

    public Supplement(String name, String activeSubstance, SupplementForm form, BigDecimal doseAmount,
                      String doseUnit, BigDecimal maxDailyDose, String missedDoseRule) {
        this.createdAt = OffsetDateTime.now();
        update(name, activeSubstance, form, doseAmount, doseUnit, maxDailyDose, missedDoseRule);
    }

    public void update(String name, String activeSubstance, SupplementForm form, BigDecimal doseAmount,
                       String doseUnit, BigDecimal maxDailyDose, String missedDoseRule) {
        this.name = name;
        this.activeSubstance = activeSubstance;
        this.form = form;
        this.doseAmount = doseAmount;
        this.doseUnit = doseUnit;
        this.maxDailyDose = maxDailyDose;
        this.missedDoseRule = missedDoseRule;
        this.updatedAt = OffsetDateTime.now();
    }

    /** Pakeičia visus platintojus nauju sąrašu (pašalinti įrašai ištrinami). */
    public void replaceDistributors(List<Distributor> newDistributors) {
        this.distributors.clear();
        this.distributors.addAll(newDistributors);
        this.updatedAt = OffsetDateTime.now();
    }

    /** Naudojamas planuose, todėl neištrinamas, o nebesiūlomas naujose rekomendacijose. */
    public void markInactive() {
        this.status = SupplementStatus.INACTIVE;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getActiveSubstance() { return activeSubstance; }
    public SupplementForm getForm() { return form; }
    public BigDecimal getDoseAmount() { return doseAmount; }
    public String getDoseUnit() { return doseUnit; }
    public BigDecimal getMaxDailyDose() { return maxDailyDose; }
    public String getMissedDoseRule() { return missedDoseRule; }
    public SupplementStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<Distributor> getDistributors() { return distributors; }
}