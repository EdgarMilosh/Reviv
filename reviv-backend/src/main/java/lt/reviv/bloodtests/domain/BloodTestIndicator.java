package lt.reviv.bloodtests.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blood_test_indicator")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BloodTestIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blood_test_id", nullable = false)
    private Long bloodTestId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "raw_value", length = 128)
    private String rawValue;

    @Column(name = "unit", length = 80)
    private String unit;

    @Column(name = "reference_interval", length = 160)
    private String referenceInterval;

    public BloodTestIndicator(
            Long bloodTestId,
            String name,
            String rawValue,
            String unit,
            String referenceInterval
    ) {
        this.bloodTestId = bloodTestId;
        this.name = name;
        this.rawValue = rawValue;
        this.unit = unit;
        this.referenceInterval = referenceInterval;
    }

    public void replaceField(
            String field,
            BloodTestIndicator extracted
    ) {
        switch (field) {
            case "NAME" -> this.name = extracted.name;
            case "VALUE" -> this.rawValue = extracted.rawValue;
            case "UNIT" -> this.unit = extracted.unit;
            case "REFERENCE" ->
                    this.referenceInterval = extracted.referenceInterval;
            default -> throw new IllegalArgumentException(
                    "Unknown indicator field: " + field
            );
        }
    }
}
