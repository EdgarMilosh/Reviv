package lt.reviv.bloodtests.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record MarkExtractionIssueRequest(

        @NotNull
        @Positive
        Long indicatorId,

        @NotBlank
        @Pattern(
                regexp = "NAME|VALUE|UNIT|REFERENCE",
                message = "Netinkama klaidos vieta."
        )
        String fieldName,

        @Size(max = 500)
        String note

) {}
