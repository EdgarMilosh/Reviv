package lt.reviv.bendra.klaidos;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class KlaiduApdorojimas {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public KlaidosAtsakymas validacija(MethodArgumentNotValidException e) {
        Map<String, String> laukai = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> laukai.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return new KlaidosAtsakymas("Patikrinkite įvestus duomenis", laukai);
    }

    @ExceptionHandler(NaudotojasNerastasException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public KlaidosAtsakymas nerastas(NaudotojasNerastasException e) {
        return new KlaidosAtsakymas(e.getMessage(), Map.of());
    }
}