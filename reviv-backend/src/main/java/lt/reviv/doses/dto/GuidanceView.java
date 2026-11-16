package lt.reviv.doses.dto;

/** Instruction shown after a dose was marked as missed; {@code supplementRule} is the catalog rule, if any. */
public record GuidanceView(String action, String message, String supplementRule) {
}