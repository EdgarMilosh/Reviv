package lt.reviv.bloodtests.service.analysis;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IndicatorDeviationCalculator {

    private static final String NUMBER =
            "[+-]?\\d+(?:[.,]\\d+)?";

    private static final Pattern VALUE = Pattern.compile(
            "^\\s*(" + NUMBER + ")\\s*$"
    );

    private static final Pattern RANGE = Pattern.compile(
            "^\\s*(" + NUMBER + ")\\s*(?:[-–—]|\\.\\.)\\s*("
                    + NUMBER + ")\\s*$"
    );

    private static final Pattern ONE_SIDED = Pattern.compile(
            "^\\s*(<=|>=|≤|≥|<|>)\\s*("
                    + NUMBER + ")\\s*$"
    );

    public enum Status {
        BELOW,
        NORMAL,
        ABOVE,
        UNKNOWN
    }

    public record DeviationResult(
            Status status,
            BigDecimal percentage
    ) {}

    public DeviationResult calculate(
            String rawValue,
            String referenceInterval
    ) {
        if (rawValue == null || referenceInterval == null) {
            return unknown();
        }

        Matcher valueMatcher = VALUE.matcher(rawValue);
        if (!valueMatcher.matches()) {
            return unknown();
        }

        BigDecimal value = parse(valueMatcher.group(1));

        Matcher range = RANGE.matcher(referenceInterval);

        if (range.matches()) {
            BigDecimal min = parse(range.group(1));
            BigDecimal max = parse(range.group(2));

            if (min.compareTo(max) > 0) {
                return unknown();
            }

            if (value.compareTo(min) < 0) {
                return deviation(Status.BELOW, value, min);
            }

            if (value.compareTo(max) > 0) {
                return deviation(Status.ABOVE, value, max);
            }

            return new DeviationResult(
                    Status.NORMAL, BigDecimal.ZERO
            );
        }

        Matcher oneSided = ONE_SIDED.matcher(referenceInterval);

        if (oneSided.matches()) {
            String operator = oneSided.group(1);
            BigDecimal limit = parse(oneSided.group(2));
            int comparison = value.compareTo(limit);

            if (operator.equals(">") && comparison <= 0
                    || (operator.equals(">=") || operator.equals("≥"))
                    && comparison < 0) {
                return deviation(Status.BELOW, value, limit);
            }

            if (operator.equals("<") && comparison >= 0
                    || (operator.equals("<=") || operator.equals("≤"))
                    && comparison > 0) {
                return deviation(Status.ABOVE, value, limit);
            }

            return new DeviationResult(
                    Status.NORMAL, BigDecimal.ZERO
            );
        }

        return unknown();
    }

    private DeviationResult deviation(
            Status status,
            BigDecimal value,
            BigDecimal boundary
    ) {
        if (boundary.compareTo(BigDecimal.ZERO) == 0) {
            return new DeviationResult(status, null);
        }

        BigDecimal percentage = value.subtract(boundary)
                .abs()
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        boundary.abs(),
                        1,
                        RoundingMode.HALF_UP
                );

        return new DeviationResult(status, percentage);
    }

    private BigDecimal parse(String number) {
        return new BigDecimal(number.replace(',', '.'));
    }

    private DeviationResult unknown() {
        return new DeviationResult(Status.UNKNOWN, null);
    }
}
