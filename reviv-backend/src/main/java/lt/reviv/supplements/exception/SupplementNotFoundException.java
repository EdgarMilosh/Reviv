package lt.reviv.supplements.exception;

public class SupplementNotFoundException extends RuntimeException {

    public SupplementNotFoundException(Long id) {
        super("Papildas nerastas (ID " + id + ")");
    }

    public SupplementNotFoundException(String message) {
        super(message);
    }
}