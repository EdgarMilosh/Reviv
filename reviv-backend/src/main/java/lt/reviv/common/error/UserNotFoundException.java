package lt.reviv.common.error;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Naudotojas dar neužsiregistravęs");
    }
}