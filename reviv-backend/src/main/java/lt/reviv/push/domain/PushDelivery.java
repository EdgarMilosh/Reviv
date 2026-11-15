package lt.reviv.push.domain;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Delivery log. The unique key (kind, subject_id, delivery_date) guarantees that the same reminder
 * is sent only once per day, even after an application restart.
 */
@Entity
@Table(name = "push_delivery")
public class PushDelivery {

    public enum Kind { INTAKE, CYCLE_ENDING, CYCLE_ENDED }

    public enum Status { PENDING, SENT, NO_SUBSCRIPTION, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    protected PushDelivery() {
    }

    public PushDelivery(Kind kind, Long subjectId, LocalDate deliveryDate) {
        this.kind = kind;
        this.subjectId = subjectId;
        this.deliveryDate = deliveryDate;
        this.createdAt = OffsetDateTime.now();
    }

    public void complete(Status result) {
        this.status = result;
        if (result == Status.SENT) {
            this.sentAt = OffsetDateTime.now();
        }
    }

    public Long getId() { return id; }
    public Kind getKind() { return kind; }
    public Long getSubjectId() { return subjectId; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public Status getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getSentAt() { return sentAt; }
}