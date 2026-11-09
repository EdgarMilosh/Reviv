package lt.reviv.profile.domain;

import jakarta.persistence.*;
import lt.reviv.users.domain.AppUser;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "height_cm")
    private Integer heightCm;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "weight_kg", precision = 5, scale = 2)
    private BigDecimal weightKg;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "harmful_habits", columnDefinition = "text[]")
    private String[] harmfulHabits = new String[0];

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allergies", columnDefinition = "text[]")
    private String[] allergies = new String[0];

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Profile() {
    }

    public Profile(AppUser user) {
        this.user = user;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Integer getHeightCm() {
        return heightCm;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public String[] getHarmfulHabits() {
        return harmfulHabits == null ? new String[0] : harmfulHabits.clone();
    }

    public String[] getAllergies() {
        return allergies == null ? new String[0] : allergies.clone();
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void update(Integer heightCm,
                       LocalDate birthDate,
                       BigDecimal weightKg,
                       String[] harmfulHabits,
                       String[] allergies) {
        this.heightCm = heightCm;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.harmfulHabits = harmfulHabits == null ? new String[0] : harmfulHabits.clone();
        this.allergies = allergies == null ? new String[0] : allergies.clone();
        this.updatedAt = OffsetDateTime.now();
    }
}
