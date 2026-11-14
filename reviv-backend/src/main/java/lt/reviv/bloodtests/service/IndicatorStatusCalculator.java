
package lt.reviv.bloodtests.service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IndicatorStatusCalculator {

    public enum Status {
        BELOW_RANGE,
        WITHIN_RANGE,
        ABOVE_RANGE,
        UNDETERMINED
    }

    private static final String NUMBER =
            "([+-]?[0-9]+(?:[.,][0-9]+)?)";

    private static final Pattern RANGE_PATTERN =
            Pattern.compile(NUMBER + "\\s*-\\s*" + NUMBER);

    private static final Pattern LIMIT_PATTERN =
            Pattern.compile("(<=|>=|<|>|≤|≥)\\s*" + NUMBER);

    public static Status calculate(
            String rawValue,
            String referenceInterval
    ) {
        if (rawValue == null || referenceInterval == null) {
            return Status.UNDETERMINED;
        }

        try {
            BigDecimal value = parseNumber(rawValue);

            String interval = referenceInterval.trim()
                    .replace("–", "-")
                    .replace("—", "-")
                    .replace("−", "-");

            // 1. Dvipusis intervalas, pvz. 30-100
            Matcher rangeMatcher = RANGE_PATTERN.matcher(interval);

            if (rangeMatcher.matches()) {
                BigDecimal min = parseNumber(rangeMatcher.group(1));
                BigDecimal max = parseNumber(rangeMatcher.group(2));

                if (min.compareTo(max) > 0) {
                    return Status.UNDETERMINED;
                }

                if (value.compareTo(min) < 0) {
                    return Status.BELOW_RANGE;
                }

                if (value.compareTo(max) > 0) {
                    return Status.ABOVE_RANGE;
                }

                return Status.WITHIN_RANGE;
            }

            // 2. Vienpusis intervalas, pvz. <20 arba >=30
            Matcher limitMatcher = LIMIT_PATTERN.matcher(interval);

            if (limitMatcher.matches()) {
                String operator = limitMatcher.group(1);
                BigDecimal limit = parseNumber(limitMatcher.group(2));

                int comparison = value.compareTo(limit);

                return switch (operator) {
                    case "<" -> comparison < 0
                            ? Status.WITHIN_RANGE
                            : Status.ABOVE_RANGE;

                    case "<=", "≤" -> comparison <= 0
                            ? Status.WITHIN_RANGE
                            : Status.ABOVE_RANGE;

                    case ">" -> comparison > 0
                            ? Status.WITHIN_RANGE
                            : Status.BELOW_RANGE;

                    case ">=", "≥" -> comparison >= 0
                            ? Status.WITHIN_RANGE
                            : Status.BELOW_RANGE;

                    default -> Status.UNDETERMINED;
                };
            }

            return Status.UNDETERMINED;

        } catch (NumberFormatException e) {
            return Status.UNDETERMINED;
        }
    }

    private static BigDecimal parseNumber(String number) {
        return new BigDecimal(
                number.trim().replace(",", ".")
        );
    }
}
