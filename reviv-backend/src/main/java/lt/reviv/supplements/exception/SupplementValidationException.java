package lt.reviv.supplements.exception;

/** Netinkami duomenys (HTTP 400): klaida priskiriama konkrečiam laukui. */
public class SupplementValidationException extends RuntimeException {

    private final String field;

    public SupplementValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}