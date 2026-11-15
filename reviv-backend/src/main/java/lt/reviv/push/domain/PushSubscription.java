package lt.reviv.push.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/** A user's browser Web Push subscription (one device). */
@Entity
@Table(name = "push_subscription")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 2048)
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PushSubscription() {
    }

    public PushSubscription(Long userId, String endpoint, String p256dh, String auth) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = OffsetDateTime.now();
    }

    /** The same browser (endpoint) may be handed to another user or receive new keys. */
    public void reassign(Long userId, String p256dh, String auth) {
        this.userId = userId;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}