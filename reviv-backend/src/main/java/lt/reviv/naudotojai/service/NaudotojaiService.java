package lt.reviv.naudotojai.service;

import lt.reviv.bendra.klaidos.NaudotojasNerastasException;
import lt.reviv.bendra.konfiguracija.RevivSavybes;
import lt.reviv.naudotojai.domain.Naudotojas;
import lt.reviv.naudotojai.domain.NaudotojoRole;
import lt.reviv.naudotojai.dto.NaudotojoInfo;
import lt.reviv.naudotojai.repository.NaudotojasRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** DP5 – Naudotojų valdymo komponentas (US-1, FR-01, FR-02). */
@Service
public class NaudotojaiService {

    private final NaudotojasRepository repository;
    private final RevivSavybes savybes;

    public NaudotojaiService(NaudotojasRepository repository, RevivSavybes savybes) {
        this.repository = repository;
        this.savybes = savybes;
    }

    /** Grąžina prisijungusį naudotoją; jei jis dar neužsiregistravęs – NaudotojasNerastasException (404). */
    @Transactional
    public NaudotojoInfo dabartinis(Jwt jwt) {
        Naudotojas n = repository.findByGoogleId(jwt.getSubject()).orElseThrow(NaudotojasNerastasException::new);
        n.atnaujintiDuomenis(elPastas(jwt), jwt.getClaimAsString("name"));
        return NaudotojoInfo.is(n);
    }

    /** Sukuria paskyrą tik gavus sutikimą (NFR-16). Pakartotinai kviečiant grąžina esamą paskyrą. */
    @Transactional
    public NaudotojoInfo registruoti(Jwt jwt) {
        return repository.findByGoogleId(jwt.getSubject())
                .map(NaudotojoInfo::is)
                .orElseGet(() -> {
                    String elPastas = elPastas(jwt);
                    NaudotojoRole role = savybes.adminEmails().contains(elPastas)
                            ? NaudotojoRole.ADMINISTRATORIUS : NaudotojoRole.NAUDOTOJAS;
                    Naudotojas naujas = new Naudotojas(jwt.getSubject(), elPastas, jwt.getClaimAsString("name"), role);
                    return NaudotojoInfo.is(repository.save(naujas));
                });
    }

    private static String elPastas(Jwt jwt) {
        return jwt.getClaimAsString("email").toLowerCase(Locale.ROOT);
    }
}