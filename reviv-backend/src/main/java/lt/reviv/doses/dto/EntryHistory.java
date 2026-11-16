package lt.reviv.doses.dto;

public record EntryHistory(
        Long entryId,
        String supplementName,
        long planned,
        long taken,
        long skipped,
        long unmarked,
        Integer adherencePercent
) {
}