package lt.reviv.common.error;

import java.util.Map;

/** Uniform error response: a general message and per-field errors. */
public record ErrorResponse(String message, Map<String, String> fields) {}