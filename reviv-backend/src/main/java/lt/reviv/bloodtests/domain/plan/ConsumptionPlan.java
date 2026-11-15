package lt.reviv.bloodtests.domain.plan;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "consumption_plan")
public class ConsumptionPlan {
    public enum Status { DRAFT, ACTIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "blood_test_id", nullable = false)
    private Long bloodTestId;
    @Column(name = "recommendation_generation_id", nullable = false)
    private Long recommendationGenerationId;
    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ConsumptionPlan() {}

    public ConsumptionPlan(Long bloodTestId, Long generationId, String aiModel) {
        this.bloodTestId = bloodTestId;
        this.recommendationGenerationId = generationId;
        this.aiModel = aiModel;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = createdAt;
    }

    public void activate() {
        status = Status.ACTIVE;
        touch();
    }
    public void touch() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public Long getBloodTestId() { return bloodTestId; }
    public Long getRecommendationGenerationId() { return recommendationGenerationId; }
    public String getAiModel() { return aiModel; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
