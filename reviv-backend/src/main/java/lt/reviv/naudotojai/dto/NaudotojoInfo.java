package lt.reviv.naudotojai.dto;

import lt.reviv.naudotojai.domain.Naudotojas;
import lt.reviv.naudotojai.domain.NaudotojoRole;

public record NaudotojoInfo(Long id, String elPastas, String vardas, NaudotojoRole role) {

    public static NaudotojoInfo is(Naudotojas n) {
        return new NaudotojoInfo(n.getId(), n.getElPastas(), n.getVardas(), n.getRole());
    }
}