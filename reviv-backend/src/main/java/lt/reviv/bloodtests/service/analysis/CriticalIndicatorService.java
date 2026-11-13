package lt.reviv.bloodtests.service.analysis;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class CriticalIndicatorService {

    public enum CriticalStatus {
        CRITICAL,
        NOT_CRITICAL_BY_RULE,
        NOT_ASSESSED
    }

    public record CriticalRule(
            String indicatorName,
            String unit,
            BigDecimal criticalBelow,
            BigDecimal criticalAbove
    ) {}

    private final List<CriticalRule> rules;

    public CriticalIndicatorService() {
        this.rules = List.of();
    }

    public CriticalIndicatorService(List<CriticalRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public CriticalStatus assess(
            String indicatorName,
            String rawValue,
            String unit
    ) {
        if (indicatorName == null
                || rawValue == null
                || unit == null) {
            return CriticalStatus.NOT_ASSESSED;
        }

        String normalizedName = indicatorName
                .trim()
                .toLowerCase(Locale.ROOT);

        BigDecimal value;

        try {
            value = new BigDecimal(
                    rawValue.trim().replace(',', '.')
            );
        } catch (NumberFormatException e) {
            return CriticalStatus.NOT_ASSESSED;
        }

        List<CriticalRule> matches = rules.stream()
                .filter(rule ->
                        rule.indicatorName().trim()
                                .toLowerCase(Locale.ROOT)
                                .equals(normalizedName)
                                && rule.unit().trim()
                                .equals(unit.trim())
                )
                .toList();

        if (matches.size() != 1) {
            return CriticalStatus.NOT_ASSESSED;
        }

        CriticalRule rule = matches.getFirst();

        if (rule.criticalBelow() == null
                && rule.criticalAbove() == null) {
            return CriticalStatus.NOT_ASSESSED;
        }

        if (rule.criticalBelow() != null
                && value.compareTo(rule.criticalBelow()) < 0) {
            return CriticalStatus.CRITICAL;
        }

        if (rule.criticalAbove() != null
                && value.compareTo(rule.criticalAbove()) > 0) {
            return CriticalStatus.CRITICAL;
        }

        return CriticalStatus.NOT_CRITICAL_BY_RULE;
    }
}
