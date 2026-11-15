package lt.reviv.push.service;

import java.time.LocalDate;

/** A plan entry whose cycle is about to end or has just ended. */
public record CycleEntry(
        Long entryId,
        Long planId,
        Long userId,
        String supplementName,
        LocalDate endDate
) {
}