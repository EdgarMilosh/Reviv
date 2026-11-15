package lt.reviv.push.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Same shape as the browser's subscription.toJSON(). */
public record PushSubscriptionRequest(
        @NotBlank(message = "Trūksta prenumeratos adreso")
        @Size(max = 2048, message = "Per ilgas prenumeratos adresas")
        @Pattern(regexp = "^https://.+", message = "Prenumeratos adresas turi prasidėti https://")
        String endpoint,

        @NotNull(message = "Trūksta prenumeratos raktų")
        @Valid
        Keys keys
) {

    public record Keys(
            @NotBlank(message = "Trūksta p256dh rakto")
            @Size(max = 255, message = "Per ilgas p256dh raktas")
            String p256dh,

            @NotBlank(message = "Trūksta auth rakto")
            @Size(max = 255, message = "Per ilgas auth raktas")
            String auth
    ) {
    }
}