package lt.reviv.common.error;

import lt.reviv.profile.exception.ProfileValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return new ErrorResponse("Please check the submitted data", fields);
    }

    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(UserNotFoundException e) {
        return new ErrorResponse(e.getMessage(), Map.of());
    }

    @ExceptionHandler(ProfileValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse profileValidation(ProfileValidationException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(e.getField(), e.getMessage());
        return new ErrorResponse("Please check the submitted data", fields);
    }
}