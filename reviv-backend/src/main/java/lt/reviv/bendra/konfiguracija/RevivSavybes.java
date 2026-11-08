package lt.reviv.bendra.konfiguracija;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Nustatymai iš application.properties, prasidedantys „reviv.“ */
@ConfigurationProperties(prefix = "reviv")
public record RevivSavybes(String googleClientId, List<String> adminEmails, List<String> corsOrigins) {

    public RevivSavybes {
        adminEmails = adminEmails == null ? List.of() : adminEmails.stream().map(String::trim).map(String::toLowerCase).toList();
        corsOrigins = corsOrigins == null ? List.of() : corsOrigins;
    }
}