package lt.reviv.bloodtests.domain.rec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "supplement_recommendation")
public class SupplementRecommendation {

    public enum RecommendationStatus {
        SUGGESTED, NOT_RECOMMENDED, NEEDS_REVIEW
    }

    public enum Decision {
        PENDING, ACCEPTED, REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "generation_id", nullable = false)
    private Long generationId;

    @Column(name = "indicator_id", nullable = false)
    private Long indicatorId;

    @Column(name = "supplement_id")
    private Long supplementId;

    @Column(name = "supplement_name_snapshot", length = 120)
    private String supplementNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_status", nullable = false, length = 24)
    private RecommendationStatus recommendationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private Decision decision = Decision.PENDING;

    @Column(name = "proposed_daily_dose", precision = 12, scale = 4)
    private BigDecimal proposedDailyDose;

    @Column(name = "dose_unit", length = 20)
    private String doseUnit;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "risk_warnings_json", nullable = false, columnDefinition = "TEXT")
    private String riskWarningsJson;

    @Column(name = "needs_specialist_review", nullable = false)
    private boolean needsSpecialistReview;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    protected SupplementRecommendation() {}

    public SupplementRecommendation(
            Long generationId,
            Long indicatorId,
            Long supplementId,
            String supplementNameSnapshot,
            RecommendationStatus recommendationStatus,
            BigDecimal proposedDailyDose,
            String doseUnit,
            Integer durationDays,
            String reason,
            String riskWarningsJson,
            boolean needsSpecialistReview
    ) {
        if (recommendationStatus == null) {
            throw new IllegalArgumentException("Rekomendacijos būsena privaloma.");
        }
        if (recommendationStatus != RecommendationStatus.SUGGESTED
                && (supplementId != null || proposedDailyDose != null
                    || doseUnit != null || durationDays != null)) {
            throw new IllegalArgumentException(
                    "Nesiūlomam papildui negali būti priskirti papildas ar dozavimas.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Sprendimo priežastis privaloma.");
        }
        this.generationId = generationId;
        this.indicatorId = indicatorId;
        this.supplementId = supplementId;
        this.supplementNameSnapshot = supplementNameSnapshot;
        this.recommendationStatus = recommendationStatus;
        this.proposedDailyDose = proposedDailyDose;
        this.doseUnit = doseUnit;
        this.durationDays = durationDays;
        this.reason = reason;
        this.riskWarningsJson = riskWarningsJson == null ? "[]" : riskWarningsJson;
        this.needsSpecialistReview = needsSpecialistReview;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    /** Saugo naudotojo pasirinkimą; tai ne medicininis dozės patvirtinimas. */
    public void decide(Decision selectedDecision) {
        if (recommendationStatus != RecommendationStatus.SUGGESTED) {
            throw new IllegalStateException("Sprendimą galima priimti tik dėl pasiūlyto papildo.");
        }
        if (decision != Decision.PENDING) {
            throw new IllegalStateException("Dėl šio pasiūlymo jau priimtas sprendimas.");
        }
        if (selectedDecision != Decision.ACCEPTED && selectedDecision != Decision.REJECTED) {
            throw new IllegalArgumentException("Leidžiami sprendimai: ACCEPTED arba REJECTED.");
        }
        this.decision = selectedDecision;
        this.decidedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getGenerationId() { return generationId; }
    public Long getIndicatorId() { return indicatorId; }
    public Long getSupplementId() { return supplementId; }
    public String getSupplementNameSnapshot() { return supplementNameSnapshot; }
    public RecommendationStatus getRecommendationStatus() { return recommendationStatus; }
    public Decision getDecision() { return decision; }
    public BigDecimal getProposedDailyDose() { return proposedDailyDose; }
    public String getDoseUnit() { return doseUnit; }
    public Integer getDurationDays() { return durationDays; }
    public String getReason() { return reason; }
    public String getRiskWarningsJson() { return riskWarningsJson; }
    public boolean isNeedsSpecialistReview() { return needsSpecialistReview; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getDecidedAt() { return decidedAt; }
}
