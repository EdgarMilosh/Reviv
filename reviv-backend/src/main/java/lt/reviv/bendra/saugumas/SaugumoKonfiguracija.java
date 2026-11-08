package lt.reviv.bendra.saugumas;

import lt.reviv.bendra.konfiguracija.RevivSavybes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SaugumoKonfiguracija {

    private static final String GOOGLE_RAKTAI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISDAVEJAI = Set.of("https://accounts.google.com", "accounts.google.com");

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, GoogleRoliuKonverteris konverteris) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/naudotojai/as").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/naudotojai/registracija").authenticated()
                        .anyRequest().hasAnyRole("NAUDOTOJAS", "ADMINISTRATORIUS"))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(konverteris)));
        return http.build();
    }

    /** Google ID žetono tikrinimas: parašas, galiojimas, išdavėjas ir mūsų Client ID. */
    @Bean
    public JwtDecoder jwtDecoder(RevivSavybes savybes) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_RAKTAI).build();
        OAuth2TokenValidator<Jwt> isdavejas = new JwtClaimValidator<String>(JwtClaimNames.ISS, GOOGLE_ISDAVEJAI::contains);
        OAuth2TokenValidator<Jwt> auditorija = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(savybes.googleClientId()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefault(), isdavejas, auditorija));
        return decoder;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(RevivSavybes savybes) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(savybes.corsOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }
}