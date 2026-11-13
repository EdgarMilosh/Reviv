package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "blood_test_analyses")
public class BloodTestAnalysis {

    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_test_id", nullable = false, unique = true)
    private Long bloodTestId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.QUEUED;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "profile_snapshot", columnDefinition = "TEXT")
    private String profileSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected BloodTestAnalysis() {}

    public BloodTestAnalysis(Long bloodTestId) {
        this.bloodTestId = bloodTestId;
    }

    public void markProcessing(String model, String profileSnapshot) {
        this.status = Status.PROCESSING;
        this.aiModel = model;
        this.profileSnapshot = profileSnapshot;
    }

    public void markCompleted() {
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }

    public Long getId() { return id; }
    public Long getBloodTestId() { return bloodTestId; }
    public Status getStatus() { return status; }
    public String getAiModel() { return aiModel; }
    public String getProfileSnapshot() { return profileSnapshot; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
