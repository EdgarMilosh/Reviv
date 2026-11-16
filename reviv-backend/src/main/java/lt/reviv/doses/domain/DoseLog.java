package lt.reviv.doses.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One marked dose: a plan entry is taken once per day, so (entry, date) identifies a dose. */
@Entity
@Table(name = "dose_log")
public class DoseLog {

    public enum Status { TAKEN, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_entry_id", nullable = false)
    private Long planEntryId;

    @Column(name = "dose_date", nullable = false)
    private LocalDate doseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "marked_at", nullable = false)
    private OffsetDateTime markedAt;

    protected DoseLog() {
    }

    public DoseLog(Long planEntryId, LocalDate doseDate, Status status, OffsetDateTime markedAt) {
        this.planEntryId = planEntryId;
        this.doseDate = doseDate;
        this.status = status;
        this.markedAt = markedAt;
    }

    public void change(Status status, OffsetDateTime markedAt) {
        this.status = status;
        this.markedAt = markedAt;
    }

    public Long getId() { return id; }
    public Long getPlanEntryId() { return planEntryId; }
    public LocalDate getDoseDate() { return doseDate; }
    public Status getStatus() { return status; }
    public OffsetDateTime getMarkedAt() { return markedAt; }
}