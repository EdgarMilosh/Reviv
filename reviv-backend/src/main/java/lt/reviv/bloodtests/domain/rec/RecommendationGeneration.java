package lt.reviv.bloodtests.domain.rec;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "recommendation_generation")
public class RecommendationGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_test_id", nullable = false)
    private Long bloodTestId;

    @Column(name = "ai_model", nullable = false, length = 100)
    private String aiModel;

    @Column(name = "profile_snapshot_json", columnDefinition = "TEXT")
    private String profileSnapshotJson;

    @Column(name = "criticality_not_assessed", nullable = false)
    private boolean criticalityNotAssessed;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RecommendationGeneration() {}

    public RecommendationGeneration(Long bloodTestId, String aiModel,
                                    String profileSnapshotJson,
                                    boolean criticalityNotAssessed) {
        this.bloodTestId = bloodTestId;
        this.aiModel = aiModel;
        this.profileSnapshotJson = profileSnapshotJson;
        this.criticalityNotAssessed = criticalityNotAssessed;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public Long getBloodTestId() { return bloodTestId; }
    public String getAiModel() { return aiModel; }
    public String getProfileSnapshotJson() { return profileSnapshotJson; }
    public boolean isCriticalityNotAssessed() { return criticalityNotAssessed; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
