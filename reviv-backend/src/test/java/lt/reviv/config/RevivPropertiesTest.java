package lt.reviv.config;

import lt.reviv.common.config.RevivProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** US-1: nustatymų (reviv.*) apdorojimas. */
class RevivPropertiesTest {

    @Test
    void TA04_adminEmailsAreTrimmedAndLowercased() {
        RevivProperties properties = new RevivProperties(
                "client-id", List.of("  Admin@Gmail.com "), List.of("http://localhost:5173"));

        assertEquals(List.of("admin@gmail.com"), properties.adminEmails());
        assertEquals(List.of("http://localhost:5173"), properties.corsOrigins());
    }

    @Test
    void TA14_missingListsBecomeEmpty() {
        RevivProperties properties = new RevivProperties("client-id", null, null);

        assertTrue(properties.adminEmails().isEmpty());
        assertTrue(properties.corsOrigins().isEmpty());
    }
}