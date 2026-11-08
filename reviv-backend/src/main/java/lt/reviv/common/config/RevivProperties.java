package lt.reviv.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Settings from application.properties starting with "reviv." */
@ConfigurationProperties(prefix = "reviv")
public record RevivProperties(String googleClientId, List<String> adminEmails, List<String> corsOrigins) {

    public RevivProperties {
        adminEmails = adminEmails == null
                ? List.of()
                : adminEmails.stream().map(String::trim).map(String::toLowerCase).toList();
        corsOrigins = corsOrigins == null ? List.of() : corsOrigins;
    }
}