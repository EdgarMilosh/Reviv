package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Set;

@Entity
@Table(name = "extraction_issue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionIssue {

    private static final Set<String> ALLOWED_FIELDS =
            Set.of("NAME", "VALUE", "UNIT", "REFERENCE");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_test_id", nullable = false)
    private Long bloodTestId;

    @Column(name = "indicator_id", nullable = false)
    private Long indicatorId;

    @Column(name = "field_name", nullable = false, length = 30)
    private String fieldName;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "resolved", nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ExtractionIssue(
            Long bloodTestId,
            Long indicatorId,
            String fieldName,
            String note
    ) {
        if (!ALLOWED_FIELDS.contains(fieldName)) {
            throw new IllegalArgumentException(
                    "Netinkamas klaidos laukas: " + fieldName
            );
        }

        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException(
                    "Klaidos komentaras per ilgas."
            );
        }

        this.bloodTestId = bloodTestId;
        this.indicatorId = indicatorId;
        this.fieldName = fieldName;
        this.note = note;
        this.resolved = false;
        this.createdAt = OffsetDateTime.now();
    }

    public void resolve() {
        this.resolved = true;
    }
}
