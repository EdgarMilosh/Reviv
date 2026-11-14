package lt.reviv.supplements.exception;

/** Duomenys jau egzistuoja (HTTP 409), pvz., pasikartojantis pavadinimas. */
public class SupplementConflictException extends RuntimeException {

    private final String field;

    public SupplementConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}