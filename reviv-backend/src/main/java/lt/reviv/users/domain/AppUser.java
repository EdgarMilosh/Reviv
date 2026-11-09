package lt.reviv.users.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lt.reviv.profile.domain.Profile;

import java.time.OffsetDateTime;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(name = "consent_date", nullable = false)
    private OffsetDateTime consentDate;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Profile profile;

    public AppUser(String googleId, String email, String name, UserRole role) {
        this.googleId = googleId;
        this.email = email;
        this.name = name;
        this.role = role;
        this.consentDate = OffsetDateTime.now();
        this.createdAt = OffsetDateTime.now();
    }

    /** If the email or name changes in the Google account, we update them. */
    public void updateDetails(String email, String name) {
        this.email = email;
        this.name = name;
    }
}