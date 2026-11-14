package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "indicator_explanations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_explanation_analysis_indicator",
                columnNames = {"analysis_id", "indicator_id"}
        )
)
public class IndicatorExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "indicator_id", nullable = false)
    private Long indicatorId;

    @Column(name = "meaning", nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(
            name = "possible_causes_json",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String possibleCausesJson;

    @Column(
            name = "practical_advice",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String practicalAdvice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IndicatorExplanation() {
    }

    public IndicatorExplanation(
            Long analysisId,
            Long indicatorId,
            String meaning,
            String possibleCausesJson,
            String practicalAdvice
    ) {
        this.analysisId = analysisId;
        this.indicatorId = indicatorId;
        this.meaning = meaning;
        this.possibleCausesJson = possibleCausesJson;
        this.practicalAdvice = practicalAdvice;
    }

    public Long getId() {
        return id;
    }

    public Long getAnalysisId() {
        return analysisId;
    }

    public Long getIndicatorId() {
        return indicatorId;
    }

    public String getMeaning() {
        return meaning;
    }

    public String getPossibleCausesJson() {
        return possibleCausesJson;
    }

    public String getPracticalAdvice() {
        return practicalAdvice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
