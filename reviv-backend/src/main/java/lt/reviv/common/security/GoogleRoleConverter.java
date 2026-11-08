package lt.reviv.common.security;

import lt.reviv.users.repository.AppUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Finds the user in the database by Google ID (sub) and grants their role.
 * An unregistered user gets no role and may only register.
 */
@Component
public class GoogleRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository repository;

    public GoogleRoleConverter(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> roles = repository.findByGoogleId(jwt.getSubject())
                .<GrantedAuthority>map(user -> new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .map(List::of)
                .orElse(List.of());
        return new JwtAuthenticationToken(jwt, roles, jwt.getSubject());
    }
}