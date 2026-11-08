package lt.reviv.bendra.saugumas;

import lt.reviv.naudotojai.repository.NaudotojasRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pagal Google ID (sub) randa naudotoją duomenų bazėje ir suteikia jo rolę.
 * Neužsiregistravęs naudotojas rolės negauna – jam leidžiama tik užsiregistruoti.
 */
@Component
public class GoogleRoliuKonverteris implements Converter<Jwt, AbstractAuthenticationToken> {

    private final NaudotojasRepository repository;

    public GoogleRoliuKonverteris(NaudotojasRepository repository) {
        this.repository = repository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> roles = repository.findByGoogleId(jwt.getSubject())
                .<GrantedAuthority>map(n -> new SimpleGrantedAuthority("ROLE_" + n.getRole().name()))
                .map(List::of)
                .orElse(List.of());
        return new JwtAuthenticationToken(jwt, roles, jwt.getSubject());
    }
}